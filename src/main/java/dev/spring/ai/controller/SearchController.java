package dev.spring.ai.controller;

import dev.spring.ai.model.ChunkResult;
import dev.spring.ai.model.SearchResponse;
import dev.spring.ai.service.AnswerService;
import dev.spring.ai.service.RetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Answers the same question two ways so the effect of reranking is visible.
 *
 * <p>Both endpoints retrieve the same Top-K chunks from the vector DB and send
 * three chunks to the LLM — the only difference is <em>which</em> three:
 * <ul>
 *   <li>{@code /without-reranking} — the first three in raw similarity order.</li>
 *   <li>{@code /with-reranking} — the three a hosted rerank model judges most
 *       relevant.</li>
 * </ul>
 * Compare the {@code chunksUsed} field of each response to see the difference.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController
{
	/** How many chunks both endpoints feed to the LLM. */
	private static final int CHUNKS_TO_LLM = 3;

	/** Retrieves and (optionally) reranks chunks from the vector store. */
	private final RetrievalService retrievalService;

	/** Generates the grounded answer from the chosen chunks. */
	private final AnswerService answerService;

	/**
	 * @param retrievalService the chunk retrieval / reranking service
	 * @param answerService    the grounded answer generator
	 */
	public SearchController(RetrievalService retrievalService, AnswerService answerService)
	{
		this.retrievalService = retrievalService;
		this.answerService    = answerService;
	}

	/**
	 * Answers using the first three chunks in raw vector-similarity order.
	 *
	 * @param query the user's question
	 * @return the answer plus the chunks used and the retrieval counts
	 */
	@GetMapping("/without-reranking")
	public SearchResponse withoutReranking(@RequestParam String query)
	{
		// Full Top-K in similarity order.
		List<ChunkResult> retrieved = retrievalService.searchWithoutReranking(query);

		// Fair comparison: feed the LLM the first three chunks in similarity order.
		List<ChunkResult> used = retrieved.subList(0, Math.min(CHUNKS_TO_LLM, retrieved.size()));
//		String answer = answerService.generateAnswer(query, used);
		String answer = null;
		return new SearchResponse(query, answer, used, retrieved.size(), used.size());
	}

	/**
	 * Answers using the three most relevant chunks after a hosted rerank step.
	 *
	 * @param query the user's question
	 * @return the answer plus the reranked chunks used and the retrieval counts
	 */
	@GetMapping("/with-reranking")
	public SearchResponse withReranking(@RequestParam String query)
	{
		// Already narrowed to the Top-N most relevant chunks.
		List<ChunkResult> reranked = retrievalService.searchWithReranking(query);
//		String answer = answerService.generateAnswer(query, reranked);
		String answer = null;
		// The DB still returned the full Top-K; rerank narrowed it to these.
		return new SearchResponse(query, answer, reranked, retrievalService.topK(), reranked.size());
	}
}
