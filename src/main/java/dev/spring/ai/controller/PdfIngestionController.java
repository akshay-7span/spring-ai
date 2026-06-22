package dev.spring.ai.controller;

import dev.spring.ai.ingestion.experiment.PdfSearchResult;
import dev.spring.ai.service.PdfVectorIngestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/pdf-ingestion")
public class PdfIngestionController
{
	private final PdfVectorIngestionService pdfVectorIngestionService;

	public PdfIngestionController(PdfVectorIngestionService pdfVectorIngestionService)
	{
		this.pdfVectorIngestionService = pdfVectorIngestionService;
	}

	// ── Ingestion ─────────────────────────────────────────────────────────────

	/**
	 * Reads the given PDF file page by page, splits each page's full text into
	 * chunks, and stores them in the {@code pdf-pages} Pinecone namespace.
	 * No metadata is attached — this is the baseline flat-text strategy.
	 *
	 * @param fileName name of the PDF inside {@code src/main/resources/sample-docs/}
	 * @return a message stating how many chunks were stored
	 */
	@PostMapping("/ingest/pages")
	public String ingestPages(@RequestParam(defaultValue = "java_notes_sample.pdf") String fileName)
	{
		int count = pdfVectorIngestionService.ingestPages(fileName);
		return count + " chunks ingested into Pinecone [pdf-pages] namespace";
	}

	/**
	 * Reads the given PDF file using position-based zone extraction (header 10%,
	 * body 80%, footer 10%), discards header and footer, splits the body into
	 * chunks, and stores them in the {@code pdf-sections} Pinecone namespace.
	 * Each chunk carries {@code sourceFile} and {@code printedPageNumber} metadata.
	 *
	 * @param fileName name of the PDF inside {@code src/main/resources/sample-docs/}
	 * @return a message stating how many chunks were stored
	 */
	@PostMapping("/ingest/sections")
	public String ingestSections(@RequestParam(defaultValue = "java_notes_sample.pdf") String fileName)
	{
		int count = pdfVectorIngestionService.ingestSections(fileName);
		return count + " chunks ingested into Pinecone [pdf-sections] namespace";
	}

	// ── Search ────────────────────────────────────────────────────────────────

	/**
	 * Searches the {@code pdf-pages} Pinecone namespace for chunks semantically
	 * similar to the given question, then calls OpenAI to produce a grounded answer.
	 *
	 * <p>Chunks in this namespace were produced from raw full-page text with no
	 * boilerplate removal and carry no metadata. Use this endpoint as the baseline
	 * to compare retrieval quality against the sections strategy.
	 *
	 * <p>Response shape:
	 * <pre>
	 * { "answer": "..." }
	 * </pre>
	 *
	 * @param question natural language question to ask
	 * @param topK     number of chunks to retrieve from Pinecone as context (default 5)
	 * @return map with {@code answer} key containing the LLM-generated response
	 */
	@GetMapping("/search/pages")
	public Map<String, Object> searchPages(
			@RequestParam String question,
			@RequestParam(defaultValue = "5") int topK)
	{
		return pdfVectorIngestionService.searchPages(question, topK);
	}

	/**
	 * Searches the {@code pdf-sections} Pinecone namespace for body-only chunks
	 * semantically similar to the given question, then calls OpenAI to produce a
	 * grounded answer, and returns the printed page numbers the answer was derived from.
	 *
	 * <p>Chunks in this namespace had header and footer stripped before storage and
	 * carry {@code sourceFile} and {@code printedPageNumber} metadata. Use this
	 * endpoint alongside {@code /search/pages} to compare whether removing boilerplate
	 * improves retrieval quality and to get citable page references.
	 *
	 * <p>Response shape:
	 * <pre>
	 * {
	 *   "answer": "...",
	 *   "sourcePages": [11, 12]
	 * }
	 * </pre>
	 *
	 * @param question natural language question to ask
	 * @param topK     number of chunks to retrieve from Pinecone as context (default 5)
	 * @return map with {@code answer} and {@code sourcePages} keys
	 */
	@GetMapping("/search/sections")
	public PdfSearchResult searchSections(
			@RequestParam String question,
			@RequestParam(defaultValue = "5") int topK)
	{
		return pdfVectorIngestionService.searchSections(question, topK);
	}
}
