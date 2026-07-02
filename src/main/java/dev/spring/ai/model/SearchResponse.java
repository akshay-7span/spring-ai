package dev.spring.ai.model;

import java.util.List;

/**
 * The response returned by both search endpoints.
 *
 * <p>The {@code chunksUsed} field is the heart of the demo: it shows exactly
 * which chunks were fed to the LLM and in what order, so the difference between
 * plain similarity retrieval and reranked retrieval is visible at a glance.
 *
 * @param query                       the original user question
 * @param answer                      the LLM's grounded answer
 * @param chunksUsed                  the chunks actually sent to the LLM (with scores)
 * @param totalChunksRetrievedFromDB  how many chunks the vector DB returned (Top-K)
 * @param totalChunksSentToLLM        how many chunks were passed to the LLM (Top-N)
 */
public record SearchResponse(
		String query,
		String answer,
		List<ChunkResult> chunksUsed,
		int totalChunksRetrievedFromDB,
		int totalChunksSentToLLM)
{
}
