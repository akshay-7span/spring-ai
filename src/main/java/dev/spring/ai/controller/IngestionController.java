package dev.spring.ai.controller;

import dev.spring.ai.service.IngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manually triggers ingestion of the employee handbook into the vector store.
 * Run this once before calling the search endpoints.
 */
@RestController
@RequestMapping("/api")
public class IngestionController
{
	/** Service that reads, chunks, embeds and stores the handbook. */
	private final IngestionService ingestionService;

	/**
	 * @param ingestionService the handbook ingestion service
	 */
	public IngestionController(IngestionService ingestionService)
	{
		this.ingestionService = ingestionService;
	}

	/**
	 * Ingests the handbook (idempotent — repeated calls skip already-stored data).
	 *
	 * @return a summary containing a status message and the number of chunks stored
	 */
	@PostMapping("/ingest")
	public Map<String, Object> ingest()
	{
		// Returns the chunk count, or 0 when ingestion was skipped.
		int total = ingestionService.ingest();

		// LinkedHashMap keeps the JSON field order predictable for readers.
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("message", "Ingestion complete");
		response.put("totalChunksIngested", total);
		return response;
	}
}
