package dev.spring.ai.model;

/**
 * A single chunk surfaced for a search, together with the score that decided
 * its position.
 *
 * <p>The meaning of {@code score} depends on which endpoint produced it:
 * <ul>
 *   <li><b>without-reranking</b> — the cosine similarity score returned by the
 *       vector database.</li>
 *   <li><b>with-reranking</b> — the relevance score returned by the hosted
 *       rerank model.</li>
 * </ul>
 *
 * @param chunkIndex the chunk's position in the original document (ingest order)
 * @param content    the chunk's text
 * @param score      similarity score (without reranking) or relevance score (with reranking)
 */
public record ChunkResult(int chunkIndex, String content, double score)
{
}
