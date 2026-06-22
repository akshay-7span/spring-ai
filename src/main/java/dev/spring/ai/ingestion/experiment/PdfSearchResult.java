package dev.spring.ai.ingestion.experiment;

import java.util.List;

/**
 * Structured output model for the PDF sections search endpoint.
 *
 * <p>Used with Spring AI's {@link org.springframework.ai.converter.BeanOutputConverter}
 * — the converter appends a JSON schema to the prompt so the LLM knows exactly what
 * shape to return, then deserializes the response directly into this record.
 * This is the same output-parsing technique demonstrated in the AI-2 branch.
 *
 * <ul>
 *   <li>{@code answer} — the LLM-generated answer based on the retrieved PDF chunks</li>
 *   <li>{@code sourcePages} — the printed page numbers the LLM actually used to
 *       answer the question (not all retrieved pages, only the relevant ones)</li>
 * </ul>
 */
public record PdfSearchResult(String answer, List<Integer> sourcePages) {}
