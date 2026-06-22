package dev.spring.ai.service;

import dev.spring.ai.ingestion.experiment.PdfSearchResult;

import java.util.Map;

public interface PdfVectorIngestionService
{
	// Ingests raw page text into 'pdf-pages' namespace — no custom metadata
	int ingestPages(String fileName);

	// Ingests body-only text into 'pdf-sections' namespace — printedPageNumber in metadata
	int ingestSections(String fileName);

	// Searches the pdf-pages namespace, calls OpenAI with retrieved chunks, returns the LLM answer
	Map<String, Object> searchPages(String question, int topK);

	// Searches pdf-sections namespace — uses BeanOutputConverter (same as AI-2) to parse answer + source pages
	PdfSearchResult searchSections(String question, int topK);
}
