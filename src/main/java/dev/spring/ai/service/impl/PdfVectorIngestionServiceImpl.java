package dev.spring.ai.service.impl;

import dev.spring.ai.ingestion.experiment.PageSections;
import dev.spring.ai.ingestion.experiment.PdfPageExtractor;
import dev.spring.ai.ingestion.experiment.PdfSearchResult;
import dev.spring.ai.ingestion.experiment.RecursiveTextSplitter;
import dev.spring.ai.service.PdfVectorIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates the full PDF → chunk → embed → store pipeline for two distinct
 * ingestion strategies:
 *
 * <ol>
 *   <li><b>Pages strategy</b> — reads each PDF page as flat text, splits it
 *       into chunks, and stores those chunks without any custom metadata in the
 *       {@code pdf-pages} Pinecone namespace.</li>
 *   <li><b>Sections strategy</b> — reads each PDF page divided into header,
 *       body, and footer zones; discards the header and footer; splits the body
 *       into chunks; and stores each chunk with {@code sourceFile} and
 *       {@code printedPageNumber} metadata in the {@code pdf-sections} Pinecone
 *       namespace.</li>
 * </ol>
 *
 * The two namespaces live in the same Pinecone index and are distinguished at
 * query time by specifying the namespace. This lets us compare retrieval quality
 * between the two strategies using the same underlying vector index.
 */
@Service
public class PdfVectorIngestionServiceImpl implements PdfVectorIngestionService
{
	private static final Logger  log              = LoggerFactory.getLogger(PdfVectorIngestionServiceImpl.class);
	private static final String  SEPARATOR        = "=".repeat(72);
	private static final String  DIVIDER          = "-".repeat(72);

	/**
	 * Regex to extract the printed page number from a PDF footer.
	 * Matches patterns like "Page 11" or "PAGE 11" (case-insensitive).
	 * Capture group 1 holds the numeric value.
	 */
	private static final Pattern PAGE_NUM_PATTERN = Pattern.compile("Page\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

	private final PdfPageExtractor      pdfPageExtractor;
	private final RecursiveTextSplitter splitter;
	private final ChatModel             chatModel;

	/** Pinecone VectorStore pointing to the {@code pdf-pages} namespace. */
	private final VectorStore pagesVectorStore;

	/** Pinecone VectorStore pointing to the {@code pdf-sections} namespace. */
	private final VectorStore sectionsVectorStore;

	public PdfVectorIngestionServiceImpl(
			PdfPageExtractor pdfPageExtractor,
			RecursiveTextSplitter splitter,
			ChatModel chatModel,
			@Qualifier("pagesVectorStore") VectorStore pagesVectorStore,
			@Qualifier("sectionsVectorStore") VectorStore sectionsVectorStore)
	{
		this.pdfPageExtractor    = pdfPageExtractor;
		this.splitter            = splitter;
		this.chatModel           = chatModel;
		this.pagesVectorStore    = pagesVectorStore;
		this.sectionsVectorStore = sectionsVectorStore;
	}

	// ── Strategy 1: Pages ─────────────────────────────────────────────────────

	/**
	 * Ingests raw page text into the {@code pdf-pages} Pinecone namespace.
	 *
	 * <p>Process:
	 * <ol>
	 *   <li>Load the PDF from {@code src/main/resources/sample-docs/}</li>
	 *   <li>Extract the full text of each page using PDFTextStripper</li>
	 *   <li>Split each page's text into chunks via {@link RecursiveTextSplitter}
	 *       (ceiling 300 tokens, overlap 50 tokens)</li>
	 *   <li>Wrap each chunk in a {@link Document} with no custom metadata</li>
	 *   <li>Send all documents to Pinecone — Spring AI embeds and stores them</li>
	 * </ol>
	 *
	 * <p>No metadata is attached here intentionally. This strategy represents
	 * the baseline: raw text with no structural awareness.
	 *
	 * @param fileName name of the PDF file inside {@code sample-docs/} (e.g. {@code java_notes_sample.pdf})
	 * @return total number of chunks stored in Pinecone
	 */
	@Override
	public int ingestPages(String fileName)
	{
		log.info(SEPARATOR);
		log.info("INGEST PAGES  : {}", fileName);
		log.info("NAMESPACE     : pdf-pages");
		log.info("METADATA      : none");
		log.info(SEPARATOR);

		// Step 1: Extract raw text from every page of the PDF (1-based page index → text)
		Map<Integer, String> pages    = loadPdfPages(fileName);
		List<Document>       documents = new ArrayList<>();

		for (Map.Entry<Integer, String> entry : pages.entrySet())
		{
			int    pageIndex = entry.getKey();
			String pageText  = entry.getValue();

			// Step 2: Split the page text into chunks.
			// RecursiveTextSplitter tries \n\n → \n → ". " → " " until every piece
			// fits within the 300-token ceiling, then merges small adjacent chunks
			// and adds a 50-token overlap tail to preserve cross-chunk context.
			List<String> chunks = splitter.split(pageText);

			log.info(DIVIDER);
			log.info("PDF PAGE {}  →  {} chunk(s)", pageIndex, chunks.size());

			// Step 3: Wrap each chunk in a Spring AI Document.
			// No metadata is added here — this is the baseline "flat page" strategy.
			for (int i = 0; i < chunks.size(); i++)
			{
				String chunk = chunks.get(i);
				log.info("  Chunk {}/{} ({} chars) : {}...",
						i + 1, chunks.size(), chunk.length(),
						chunk.substring(0, Math.min(80, chunk.length())).replace("\n", " "));
				documents.add(new Document(chunk));
			}
		}

		log.info(SEPARATOR);
		log.info("TOTAL CHUNKS  : {} — sending to Pinecone [pdf-pages]", documents.size());
		log.info(SEPARATOR);

		// Step 4: Send all documents to Pinecone in a single batch.
		// Spring AI automatically embeds each document using the configured
		// EmbeddingModel (text-embedding-3-small) before storing.
		pagesVectorStore.add(documents);

		log.info("INGESTION COMPLETE — {} chunks stored in [pdf-pages]", documents.size());
		log.info(SEPARATOR);

		return documents.size();
	}

	// ── Strategy 2: Sections ──────────────────────────────────────────────────

	/**
	 * Ingests body-only text into the {@code pdf-sections} Pinecone namespace,
	 * with printed page number attached as metadata.
	 *
	 * <p>Process:
	 * <ol>
	 *   <li>Load the PDF from {@code src/main/resources/sample-docs/}</li>
	 *   <li>Split each page into three zones using position-based extraction:
	 *       top 10% = header, middle 80% = body, bottom 10% = footer</li>
	 *   <li>Parse the printed page number from the footer text using regex
	 *       (e.g. "JAVA PROGRAMMING  Page 11" → {@code 11})</li>
	 *   <li>Discard header and footer; split body text into chunks</li>
	 *   <li>Attach {@code sourceFile} and {@code printedPageNumber} metadata
	 *       to each chunk before storing in Pinecone</li>
	 * </ol>
	 *
	 * <p>Metadata-enriched chunks enable precise attribution at retrieval time —
	 * you know exactly which printed page a retrieved chunk came from, which is
	 * useful for citations and debugging retrieval quality.
	 *
	 * @param fileName name of the PDF file inside {@code sample-docs/} (e.g. {@code java_notes_sample.pdf})
	 * @return total number of chunks stored in Pinecone
	 */
	@Override
	public int ingestSections(String fileName)
	{
		log.info(SEPARATOR);
		log.info("INGEST SECTIONS : {}", fileName);
		log.info("NAMESPACE       : pdf-sections");
		log.info("METADATA        : printedPageNumber, sourceFile");
		log.info(SEPARATOR);

		// Step 1: Extract header/body/footer zones from every page.
		// PDFTextStripperByArea divides the page by pixel coordinates:
		// header = top 10%, body = middle 80%, footer = bottom 10%.
		List<PageSections> sections  = loadPdfSections(fileName);
		List<Document>     documents = new ArrayList<>();

		for (PageSections page : sections)
		{
			// Step 2: Parse the printed page number from the footer text.
			// The footer often contains "Page 11" or "PAGE 11".
			// This is different from the PDF page index (e.g. PDF page 3 might say "Page 11").
			Integer printedPageNumber = parsePageNumber(page.footer());

			log.info(DIVIDER);
			log.info("PDF PAGE INDEX {}  |  Printed page: {}  |  Footer: \"{}\"",
					page.pageNumber(), printedPageNumber != null ? printedPageNumber : "not found", page.footer());

			// Step 3: Split the body text only.
			// Header and footer are intentionally discarded because they contain
			// repeated boilerplate (title, page number) that would pollute every chunk.
			List<String> chunks = splitter.split(page.body());
			log.info("Body → {} chunk(s)", chunks.size());

			// Step 4: Wrap each chunk in a Document with metadata.
			// sourceFile lets us filter by document at query time.
			// printedPageNumber links the chunk back to the human-readable page,
			// useful for citations (shown only when the footer contained a page number).
			for (int i = 0; i < chunks.size(); i++)
			{
				String chunk = chunks.get(i);

				Map<String, Object> metadata = new HashMap<>();
				metadata.put("sourceFile", fileName);
				if (printedPageNumber != null)
					metadata.put("printedPageNumber", printedPageNumber);

				log.info("  Chunk {}/{} ({} chars) : {}...",
						i + 1, chunks.size(), chunk.length(),
						chunk.substring(0, Math.min(80, chunk.length())).replace("\n", " "));

				documents.add(new Document(chunk, metadata));
			}
		}

		log.info(SEPARATOR);
		log.info("TOTAL CHUNKS  : {} — sending to Pinecone [pdf-sections]", documents.size());
		log.info(SEPARATOR);

		// Step 5: Send all documents to Pinecone in a single batch.
		// Spring AI embeds each document and stores it along with its metadata vector.
		sectionsVectorStore.add(documents);

		log.info("INGESTION COMPLETE — {} chunks stored in [pdf-sections]", documents.size());
		log.info(SEPARATOR);

		return documents.size();
	}

	// ── Search ────────────────────────────────────────────────────────────────

	/**
	 * Retrieves the top-K chunks from the {@code pdf-pages} namespace that are most
	 * semantically similar to the question, assembles them into a context prompt,
	 * and calls OpenAI to generate a grounded answer.
	 *
	 * <p>Response shape:
	 * <pre>
	 * {
	 *   "answer": "LLM generated answer based on retrieved chunks"
	 * }
	 * </pre>
	 *
	 * <p>No source page information is available here because the {@code pdf-pages}
	 * strategy stores chunks without metadata.
	 *
	 * @param question the user's natural language question
	 * @param topK     number of chunks to retrieve from Pinecone before calling the LLM
	 * @return map containing the {@code answer} key with the LLM's response
	 */
	@Override
	public Map<String, Object> searchPages(String question, int topK)
	{
		log.info(SEPARATOR);
		log.info("SEARCH [pdf-pages]  QUESTION : {}", question);
		log.info("TOP-K              : {}", topK);
		log.info(SEPARATOR);

		// Step 1: Embed the question and retrieve the most similar chunks from Pinecone
		List<Document> hits = pagesVectorStore.similaritySearch(
				SearchRequest.builder().query(question).topK(topK).build()
		);

		log.info("RETRIEVED {} CHUNK(S) FROM [pdf-pages]", hits.size());
		for (int i = 0; i < hits.size(); i++)
		{
			String text = hits.get(i).getText();
			log.info(DIVIDER);
			log.info("  Chunk {}/{} ({} chars)", i + 1, hits.size(), text != null ? text.length() : 0);
			log.info("  {}", text != null ? text.replace("\n", " ") : "(empty)");
		}
		log.info(SEPARATOR);

		// Step 2: Concatenate retrieved chunks into a single context block,
		// numbered so the LLM can reference which chunk it used
		StringBuilder contextBuilder = new StringBuilder();
		for (int i = 0; i < hits.size(); i++)
		{
			contextBuilder.append("[Chunk ").append(i + 1).append("]:\n");
			contextBuilder.append(hits.get(i).getText()).append("\n\n");
		}
		String context = contextBuilder.toString().trim();

		// Step 3: Build the RAG prompt — ask for a detailed answer from the context only
		String promptText = """
				You are a helpful assistant. Answer the question in detail using only the context provided below.
				If the answer is not present in the context, say "I could not find the answer in the provided document."

				Context:
				%s

				Question: %s
				""".formatted(context, question);

		log.info("CALLING OPENAI WITH {} CONTEXT CHUNK(S)...", hits.size());

		// Step 4: Call OpenAI and extract the text response
		String answer = chatModel.call(new Prompt(promptText))
				.getResult()
				.getOutput()
				.getText();

		log.info("LLM ANSWER : {}", answer);
		log.info(SEPARATOR);

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("answer", answer);
		return response;
	}

	/**
	 * Retrieves the top-K chunks from the {@code pdf-sections} namespace that are most
	 * semantically similar to the question, assembles them into a context prompt,
	 * calls OpenAI to generate a grounded answer, and collects the source page numbers
	 * from metadata so the caller knows exactly where the answer came from.
	 *
	 * <p>Response shape:
	 * <pre>
	 * {
	 *   "answer": "LLM generated answer based on retrieved body chunks",
	 *   "sourcePages": [11, 12]
	 * }
	 * </pre>
	 *
	 * <p>{@code sourcePages} lists the unique printed page numbers of every chunk that
	 * was used as context, in the order they were retrieved. This allows the caller to
	 * cite the exact page(s) the answer was derived from.
	 *
	 * @param question the user's natural language question
	 * @param topK     number of chunks to retrieve from Pinecone before calling the LLM
	 * @return map containing {@code answer} and {@code sourcePages} keys
	 */
	@Override
	public PdfSearchResult searchSections(String question, int topK)
	{
		log.info(SEPARATOR);
		log.info("SEARCH [pdf-sections]  QUESTION : {}", question);
		log.info("TOP-K                 : {}", topK);
		log.info(SEPARATOR);

		// Step 1: Embed the question and retrieve the most similar body chunks from Pinecone
		List<Document> hits = sectionsVectorStore.similaritySearch(
				SearchRequest.builder().query(question).topK(topK).build()
		);

		log.info("RETRIEVED {} CHUNK(S) FROM [pdf-sections]", hits.size());
		for (int i = 0; i < hits.size(); i++)
		{
			String              text = hits.get(i).getText();
			Map<String, Object> meta = hits.get(i).getMetadata();
			log.info(DIVIDER);
			log.info("  Chunk {}/{}  |  page: {}  |  source: {}  |  ({} chars)",
					i + 1, hits.size(),
					pageLabel(meta.get("printedPageNumber")),
					meta.getOrDefault("sourceFile", "?"),
					text != null ? text.length() : 0);
			log.info("  {}", text != null ? text.replace("\n", " ") : "(empty)");
		}
		log.info(SEPARATOR);

		// Step 2: Build a labeled context block — each chunk is prefixed with its page number
		// so the LLM knows which page each piece of information comes from
		StringBuilder contextBuilder = new StringBuilder();
		for (Document doc : hits)
		{
			String page = pageLabel(doc.getMetadata().get("printedPageNumber"));
			contextBuilder.append("[Page ").append(page).append("]:\n");
			contextBuilder.append(doc.getText()).append("\n\n");
		}
		String context = contextBuilder.toString().trim();

		// Step 3: Create a BeanOutputConverter for PdfSearchResult.
		// This is the same output-parsing pattern from AI-2.
		// The converter automatically appends a JSON schema to the prompt so the LLM
		// knows the exact shape to return, then deserializes the response into the record.
		BeanOutputConverter<PdfSearchResult> converter = new BeanOutputConverter<>(PdfSearchResult.class);

		String promptText = """
				You are a helpful assistant. Answer the question in detail using only the context provided below.
				Each context block is labeled with its page number, e.g. [Page 2].
				If the answer is not present in the context, say "I could not find the answer in the provided document."

				Context:
				%s

				Question: %s

				%s
				""".formatted(context, question, converter.getFormat());

		log.info("CALLING OPENAI WITH {} LABELED CONTEXT CHUNK(S)...", hits.size());

		// Step 4: Call OpenAI and let BeanOutputConverter parse the response into PdfSearchResult
		String raw = chatModel.call(new Prompt(promptText))
				.getResult()
				.getOutput()
				.getText();

		log.info("RAW LLM RESPONSE : {}", raw);

		PdfSearchResult result = converter.convert(raw);

		log.info("LLM ANSWER  : {}", result != null ? result.answer() : "null");
		log.info("SOURCE PAGES: {}", result != null ? result.sourcePages() : "[]");
		log.info(SEPARATOR);

		return result;
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	/**
	 * Resolves the PDF file from the classpath and delegates to
	 * {@link PdfPageExtractor#extractPagesText(File)} to get a map of
	 * page index → full page text.
	 */
	private Map<Integer, String> loadPdfPages(String fileName)
	{
		try
		{
			File pdfFile = new ClassPathResource("sample-docs/" + fileName).getFile();
			return pdfPageExtractor.extractPagesText(pdfFile);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Could not load PDF: sample-docs/" + fileName, e);
		}
	}

	/**
	 * Resolves the PDF file from the classpath and delegates to
	 * {@link PdfPageExtractor#extractPageSections(File)} to get a list of
	 * per-page header/body/footer sections.
	 */
	private List<PageSections> loadPdfSections(String fileName)
	{
		try
		{
			File pdfFile = new ClassPathResource("sample-docs/" + fileName).getFile();
			return pdfPageExtractor.extractPageSections(pdfFile);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Could not load PDF: sample-docs/" + fileName, e);
		}
	}

	/**
	 * Converts a page number from Pinecone metadata to a clean integer string.
	 * Pinecone returns numeric metadata as Double (e.g. 2.0), so we strip the decimal.
	 */
	private String pageLabel(Object pageValue)
	{
		if (pageValue == null) return "?";
		if (pageValue instanceof Double d) return String.valueOf(d.intValue());
		return pageValue.toString();
	}

	/**
	 * Extracts the printed page number from a footer string using regex.
	 *
	 * <p>Example: {@code "JAVA PROGRAMMING  Page 11"} → {@code 11}
	 *
	 * @param footerText raw text extracted from the footer zone
	 * @return the parsed page number, or {@code null} if no match is found
	 */
	private Integer parsePageNumber(String footerText)
	{
		if (footerText == null || footerText.isBlank()) return null;
		Matcher matcher = PAGE_NUM_PATTERN.matcher(footerText);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
	}
}
