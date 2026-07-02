package dev.spring.ai.service;

import dev.spring.ai.model.ChunkResult;
import io.pinecone.clients.Pinecone;
import org.openapitools.inference.client.ApiException;
import org.openapitools.inference.client.model.RankedDocument;
import org.openapitools.inference.client.model.RerankResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieves handbook chunks for a query in two ways so the demo can compare them:
 *
 * <ul>
 *   <li>{@link #searchWithoutReranking(String)} — returns the Top-K chunks in the
 *       raw cosine-similarity order the vector DB gives back.</li>
 *   <li>{@link #searchWithReranking(String)} — takes those same Top-K chunks and
 *       hands them to Pinecone's hosted rerank model, returning the Top-N reordered
 *       by relevance.</li>
 * </ul>
 *
 * <p>Both paths start from an identical vector search; the only difference is the
 * extra rerank call, which makes the comparison fair.
 */
@Service
public class RetrievalService
{
	private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

	/** Field the reranker reads from each candidate document. */
	private static final String RANK_FIELD = "content";

	/** Horizontal rule used to frame the chunk dump in the logs. */
	private static final String DIVIDER = "-".repeat(72);

	/** Spring AI vector store used for the Top-K similarity search. */
	private final VectorStore handbookVectorStore;

	/** Raw Pinecone client used only for the hosted rerank call. */
	private final Pinecone pinecone;

	/** Hosted rerank model name (e.g. {@code bge-reranker-v2-m3}). */
	private final String rerankModel;

	/** How many chunks to pull from the vector DB. */
	private final int topK;

	/** How many chunks to keep after reranking. */
	private final int topN;

	/**
	 * @param handbookVectorStore the Pinecone-backed vector store bean
	 * @param pinecone            the raw Pinecone client (rerank only)
	 * @param rerankModel         the hosted rerank model name (from {@code rerank.model})
	 * @param topK                chunks to retrieve from the vector DB (from {@code search.top-k})
	 * @param topN                chunks to keep after reranking (from {@code search.top-n})
	 */
	public RetrievalService(
			VectorStore handbookVectorStore,
			Pinecone pinecone,
			@Value("${rerank.model}") String rerankModel,
			@Value("${search.top-k}") int topK,
			@Value("${search.top-n}") int topN)
	{
		this.handbookVectorStore = handbookVectorStore;
		this.pinecone            = pinecone;
		this.rerankModel         = rerankModel;
		this.topK                = topK;
		this.topN                = topN;
	}

	/**
	 * Returns the Top-K chunks in vector-similarity order. The {@code score} on
	 * each result is the cosine similarity reported by the vector DB.
	 *
	 * @param query the user's question
	 * @return the Top-K chunks, highest similarity first
	 */
	public List<ChunkResult> searchWithoutReranking(String query)
	{
		List<Document> hits = retrieveTopK(query);

		// Map each hit straight through, keeping the DB's similarity score.
		List<ChunkResult> results = new ArrayList<>(hits.size());
		for (Document doc : hits)
		{
			results.add(toChunkResult(doc, doc.getScore()));
		}

		// Print the retrieved chunks so the blog can show what plain similarity returns.
		logChunks("WITHOUT RERANKING — vector DB similarity order", query, results);
		return results;
	}

	/**
	 * Returns the Top-N chunks after a separate hosted rerank call. The
	 * {@code score} on each result is the rerank relevance score.
	 *
	 * @param query the user's question
	 * @return the Top-N chunks, highest relevance first
	 */
	public List<ChunkResult> searchWithReranking(String query)
	{
		// Start from the exact same Top-K the plain endpoint uses.
		List<Document> hits = retrieveTopK(query);

		// Print the candidates going into the reranker, in similarity order, so the
		// blog can compare "before" against the reranked "after" below.
		List<ChunkResult> candidates = new ArrayList<>(hits.size());
		for (Document doc : hits)
		{
			candidates.add(toChunkResult(doc, doc.getScore()));
		}
		logChunks("WITH RERANKING — Top-K candidates BEFORE rerank", query, candidates);

		// The reranker reads the "content" field of each candidate document.
		// Map.of rejects null values, so fall back to empty text defensively.
		List<Map<String, Object>> documents = new ArrayList<>(hits.size());
		for (Document doc : hits)
		{
			String content = doc.getText();
			documents.add(Map.of(RANK_FIELD, content != null ? content : ""));
		}

		// Separate hosted call: Pinecone scores every candidate against the query.
		RerankResult rerank = callRerank(query, documents);

		// Map each ranked result back to its original chunk via the returned index.
		List<ChunkResult> results = new ArrayList<>(rerank.getData().size());
		List<Integer> similarityRanks = new ArrayList<>(rerank.getData().size());
		for (RankedDocument ranked : rerank.getData())
		{
			// getIndex() points into the documents list we sent, which is parallel
			// to the original hits, so we recover the chunk's metadata + text.
			Document original = hits.get(ranked.getIndex());
			results.add(toChunkResult(original, ranked.getScore().doubleValue()));

			// That same index is the chunk's 0-based slot in the similarity-ordered
			// Top-K, so +1 is the rank it held *before* reranking.
			similarityRanks.add(ranked.getIndex() + 1);
		}

		// Print the reranked result so the relevance reordering is visible, showing
		// how far each chunk moved from its similarity rank and how strong the match is.
		logRerankedChoices(query, results, similarityRanks);
		return results;
	}

	/**
	 * The configured Top-K — how many chunks each search pulls from the vector DB.
	 *
	 * @return the Top-K value
	 */
	public int topK()
	{
		return topK;
	}

	/**
	 * Runs the Top-K cosine-similarity search against the vector store.
	 *
	 * @param query the user's question
	 * @return up to Top-K matching documents (never {@code null})
	 */
	private List<Document> retrieveTopK(String query)
	{
		return handbookVectorStore.similaritySearch(
				SearchRequest.builder().query(query).topK(topK).build());
	}

	/**
	 * Calls Pinecone's hosted Inference Rerank API.
	 *
	 * @param query     the user's question
	 * @param documents the candidate chunks, each as a {@code {"content": text}} map
	 * @return the rerank result (Top-N documents with relevance scores)
	 * @throws IllegalStateException if the rerank API call fails
	 */
	private RerankResult callRerank(String query, List<Map<String, Object>> documents)
	{
		try
		{
			return pinecone.getInferenceClient().rerank(
					rerankModel,
					query,
					documents,
					List.of(RANK_FIELD),
					topN,
					true,
					Map.of("truncate", "END"));
		}
		catch (ApiException e)
		{
			throw new IllegalStateException("Pinecone rerank call failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Converts a retrieved {@link Document} into the API-facing {@link ChunkResult}.
	 *
	 * @param doc   the retrieved document (carrying {@code chunkIndex} metadata)
	 * @param score the score to attach — cosine similarity or rerank relevance
	 * @return the corresponding chunk result
	 */
	private ChunkResult toChunkResult(Document doc, Double score)
	{
		Object chunkIndex = doc.getMetadata().get("chunkIndex");
		int index = chunkIndex instanceof Number n ? n.intValue() : -1;
		return new ChunkResult(index, doc.getText(), score != null ? score : 0.0);
	}

	/**
	 * Prints a set of similarity-ranked chunks so the blog can showcase exactly
	 * which chunks the vector DB returned and in what order. The score shown is the
	 * cosine <em>similarity</em> as a percentage — how close the chunk's meaning is
	 * to the query, regardless of whether it actually answers it.
	 *
	 * @param label  a heading describing this set (e.g. without-reranking / before rerank)
	 * @param query  the query these chunks were retrieved for
	 * @param chunks the chunks to print, in similarity order
	 */
	private void logChunks(String label, String query, List<ChunkResult> chunks)
	{
		StringBuilder sb = new StringBuilder(System.lineSeparator());
		sb.append(DIVIDER).append(System.lineSeparator());
		sb.append(label).append(System.lineSeparator());
		sb.append("Score = Similarity: how close the chunk is to the query in meaning")
				.append(System.lineSeparator());
		sb.append("Query: ").append(query)
				.append("  |  ").append(chunks.size()).append(" chunk(s)")
				.append(System.lineSeparator());
		sb.append(DIVIDER).append(System.lineSeparator());

		for (int i = 0; i < chunks.size(); i++)
		{
			ChunkResult c = chunks.get(i);
			sb.append(String.format(
					"[%d] chunkIndex=%d  Similarity %.1f%%", i + 1, c.chunkIndex(), c.score() * 100))
					.append(System.lineSeparator())
					.append(c.content())
					.append(System.lineSeparator())
					.append(DIVIDER).append(System.lineSeparator());
		}

		log.info(sb.toString());
	}

	/**
	 * Prints the reranked chunks in a reader-friendly way, using only the numbers
	 * the rerank model already returns. For each chunk it shows its relevance as a
	 * percentage plus a plain-language band, and how far it moved from its original
	 * similarity rank — the movement is the data-driven reason it was chosen over
	 * the chunks the reranker dropped.
	 *
	 * @param query           the query these chunks were retrieved for
	 * @param chunks          the reranked chunks, in final relevance order
	 * @param similarityRanks each chunk's 1-based rank in the pre-rerank similarity order
	 */
	private void logRerankedChoices(String query, List<ChunkResult> chunks, List<Integer> similarityRanks)
	{
		StringBuilder sb = new StringBuilder(System.lineSeparator());
		sb.append(DIVIDER).append(System.lineSeparator());
		sb.append("WITH RERANKING — Top-N AFTER rerank (why these ")
				.append(chunks.size()).append(" were chosen)").append(System.lineSeparator());
		sb.append("Score = Relevance: how well the chunk actually answers the query")
				.append(System.lineSeparator());
		sb.append("Query: ").append(query).append(System.lineSeparator());
		sb.append(DIVIDER).append(System.lineSeparator());

		for (int i = 0; i < chunks.size(); i++)
		{
			ChunkResult c = chunks.get(i);
			int newRank = i + 1;
			int oldRank = similarityRanks.get(i);

			sb.append(String.format(
					"#%d  Relevance %.1f%% (%s)  %s",
					newRank, c.score() * 100, relevanceBand(c.score()), rankMovement(oldRank, newRank)))
					.append(System.lineSeparator())
					.append("chunkIndex=").append(c.chunkIndex()).append(System.lineSeparator())
					.append(c.content()).append(System.lineSeparator())
					.append(DIVIDER).append(System.lineSeparator());
		}

		log.info(sb.toString());
	}

	/**
	 * Maps a rerank relevance score (0..1) to a plain-language band so a reader can
	 * judge the number at a glance instead of interpreting a raw decimal.
	 *
	 * @param score the rerank relevance score
	 * @return a short human-readable band
	 */
	private static String relevanceBand(double score)
	{
		if (score >= 0.75) return "Excellent match";
		if (score >= 0.50) return "Strong match";
		if (score >= 0.30) return "Moderate match";
		return "Weak match";
	}

	/**
	 * Describes how a chunk moved from its similarity rank to its rerank rank — the
	 * visible proof of what reranking changed.
	 *
	 * @param oldRank the chunk's rank before reranking (by similarity)
	 * @param newRank the chunk's rank after reranking (by relevance)
	 * @return a short description such as "↑ up from #4 by similarity"
	 */
	private static String rankMovement(int oldRank, int newRank)
	{
		if (oldRank == newRank) return "= held its #" + newRank + " spot";
		if (oldRank > newRank)  return "↑ up from #" + oldRank + " by similarity";
		return "↓ down from #" + oldRank + " by similarity";
	}
}
