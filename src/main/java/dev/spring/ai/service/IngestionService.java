package dev.spring.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the employee handbook, splits it into token-sized chunks, embeds each
 * chunk and stores it in the Pinecone {@code employee-handbook} namespace.
 *
 * <p>Every chunk carries two pieces of metadata so the search responses can
 * report exactly which part of the document was used:
 * <ul>
 *   <li>{@code chunkIndex} — the chunk's position in ingest order</li>
 *   <li>{@code sourceFile} — the file the chunk came from</li>
 * </ul>
 *
 * <p>This is the first step a user runs: without ingested data the search
 * endpoints have nothing to retrieve.
 */
@Service
public class IngestionService
{
	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	/** Target tokens per chunk — small enough that reranking has distinct candidates to choose between. */
	private static final int CHUNK_SIZE_TOKENS = 300;

	/** Spring AI vector store backed by Pinecone; handles embedding + storage + similarity search. */
	private final VectorStore handbookVectorStore;

	/** Logical name of the handbook file, stored on every chunk as {@code sourceFile} metadata. */
	private final String sourceFile;

	/** The handbook file on the classpath, resolved from {@code search.source-file}. */
	private final Resource handbookResource;

	/**
	 * @param handbookVectorStore the Pinecone-backed vector store bean
	 * @param sourceFile          the handbook file name (from {@code search.source-file})
	 * @param handbookResource    the same file resolved as a classpath resource
	 */
	public IngestionService(
			VectorStore handbookVectorStore,
			@Value("${search.source-file}") String sourceFile,
			@Value("classpath:${search.source-file}") Resource handbookResource)
	{
		this.handbookVectorStore = handbookVectorStore;
		this.sourceFile          = sourceFile;
		this.handbookResource    = handbookResource;
	}

	/**
	 * Ingests the handbook into the vector store.
	 *
	 * <p>Ingestion is idempotent: if the namespace already holds data, it is
	 * skipped so repeated calls (or app restarts) don't create duplicates.
	 *
	 * @return the number of chunks stored, or {@code 0} if ingestion was skipped
	 */
	public int ingest()
	{
		log.info("Ingestion starting for '{}'", sourceFile);

		// Guard against duplicate ingestion on repeated calls / restarts.
		if (alreadyIngested())
		{
			log.info("Namespace already populated — skipping ingestion");
			return 0;
		}

		// Load the raw handbook text from the classpath.
		String text = readHandbook();

		// Split the whole document into ~300-token chunks.
		TokenTextSplitter splitter = TokenTextSplitter.builder()
				.withChunkSize(CHUNK_SIZE_TOKENS)
				.build();
		List<Document> chunks = splitter.apply(List.of(new Document(text)));

		// Re-wrap each chunk with its ingest-order index + source file as metadata.
		// The splitter can emit empty/null fragments, so skip those — Document
		// rejects null text and would throw an NPE.
		List<Document> documents = new ArrayList<>(chunks.size());
		int chunkIndex = 0;
		for (Document chunk : chunks)
		{
			String content = chunk.getText();
			if (content == null || content.isBlank())
			{
				continue;
			}

			Map<String, Object> metadata = new HashMap<>();
			metadata.put("chunkIndex", chunkIndex++);
			metadata.put("sourceFile", sourceFile);
			documents.add(new Document(content, metadata));
		}

		// Embed + store every chunk in one call.
		handbookVectorStore.add(documents);
		log.info("Ingestion complete — stored {} chunks", documents.size());
		return documents.size();
	}

	/**
	 * Probes the namespace with a cheap one-result search to detect existing data.
	 *
	 * @return {@code true} if the namespace already contains at least one chunk
	 */
	private boolean alreadyIngested()
	{
		List<Document> hits = handbookVectorStore.similaritySearch(
				SearchRequest.builder().query("the").topK(1).build());
		return !hits.isEmpty();
	}

	/**
	 * Reads the entire handbook file into a string.
	 *
	 * @return the handbook contents as UTF-8 text
	 * @throws IllegalStateException if the file cannot be read
	 */
	private String readHandbook()
	{
		try
		{
			return StreamUtils.copyToString(handbookResource.getInputStream(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read handbook file: " + sourceFile, e);
		}
	}
}
