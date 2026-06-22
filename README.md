# AI-8 — PDF Ingestion Quality Experiment

## What This Branch Demonstrates

This branch explores how **PDF parsing strategy affects RAG answer quality**. When building a Retrieval-Augmented Generation (RAG) system, the accuracy of your LLM answers depends heavily on what you store in your vector database — not just which LLM you use.

We implement two ingestion strategies for the same PDF, store them in separate Pinecone namespaces, and expose search endpoints for both so you can compare results side by side.

**The core question:** Does smarter PDF parsing produce better answers?

---

## Why This Matters

A naive approach reads each PDF page as raw text and stores it as-is. The problem is that most PDFs contain repeated boilerplate on every page — college names, course titles, page numbers — that appear in almost every chunk. This pollutes the embeddings and causes the vector search to retrieve less relevant content.

By identifying and discarding boilerplate before chunking, we get cleaner chunks, better retrieval, and more accurate LLM answers. The sections strategy also stores the printed page number as metadata, so every answer can cite exactly which page it came from.

---

## Tech Stack

- Java 21
- Spring Boot 3.5.14
- Spring AI 1.0.8
- OpenAI `gpt-4.1-nano` (chat) + `text-embedding-3-small` (embeddings, 1536 dimensions)
- Apache PDFBox 3.0.3 (PDF parsing)
- Pinecone (vector database — one index, two namespaces)

---

## Features

| Feature | Details |
|---|---|
| Flat page extraction | Reads each PDF page as raw text using `PDFTextStripper` |
| Position-based section extraction | Divides each page into header (10%), body (80%), footer (10%) zones using `PDFTextStripperByArea` |
| Boilerplate removal | Header and footer zones are discarded before chunking |
| Printed page number extraction | Footer text is parsed with regex to extract the human-readable page number |
| Recursive text splitter | Hierarchical splitting: `\n\n → \n → ". " → " "`, ceiling 300 tokens, overlap 50 tokens |
| Two Pinecone namespaces | `pdf-pages` (flat) and `pdf-sections` (body-only with metadata) within one index |
| Metadata on chunks | `sourceFile` and `printedPageNumber` stored per chunk in the sections namespace |
| RAG search — pages | Retrieves chunks from `pdf-pages`, sends to OpenAI, returns answer |
| RAG search — sections | Retrieves chunks from `pdf-sections`, sends to OpenAI, returns answer + source page numbers |
| Structured LLM output | `BeanOutputConverter<PdfSearchResult>` (same pattern as AI-2) parses LLM JSON response into a typed record |

---

## Key Concepts Explained

### Index vs Namespace (Pinecone)
- **Index** — a completely separate database with its own host URL. Suitable when datasets are entirely unrelated.
- **Namespace** — a logical partition inside one index. Same host, same dimensions. Like a folder within the index.

This branch uses **one index with two namespaces** (`pdf-pages` and `pdf-sections`). This is the recommended pattern when comparing strategies on the same dataset — no extra infrastructure cost, and both namespaces are queryable independently.

### Two VectorStore Beans
Spring AI auto-configures one `VectorStore` bean by default. To have two beans pointing to different namespaces, both are defined manually in `PineconeConfiguration`. One is marked `@Primary` (which suppresses the auto-config's `@ConditionalOnMissingBean`), and the other is injected by name using `@Qualifier`.

### Recursive Text Splitter
The splitter works in three phases:
1. **Split** — recursively divide text at natural boundaries until every piece fits under 300 tokens
2. **Merge** — greedily combine adjacent small pieces back up to 300 tokens (avoids many tiny single-sentence chunks)
3. **Overlap** — prepend the last 50 tokens of chunk N to chunk N+1 (preserves cross-boundary context)

Token count is estimated as `wordCount × 1.3` — a reliable approximation for English text with no external tokenizer needed.

### BeanOutputConverter
The sections search endpoint needs the LLM to return both an answer and which pages it used. `BeanOutputConverter<PdfSearchResult>` automatically appends a JSON schema to the prompt and deserialises the response into a typed Java record — the same output-parsing technique from AI-2.

---

## Project Structure

```
src/main/java/dev/spring/ai/
├── config/
│   └── PineconeConfiguration.java       # Two VectorStore beans (@Primary + @Qualifier)
├── controller/
│   └── PdfIngestionController.java      # REST endpoints
├── ingestion/experiment/
│   ├── PageSections.java                # Record: header, body, footer per page
│   ├── PdfPageExtractor.java            # PDFBox extraction (flat + position-based)
│   ├── PdfSearchResult.java             # Record: answer + sourcePages (BeanOutputConverter target)
│   └── RecursiveTextSplitter.java       # 3-phase hierarchical chunker
└── service/
    ├── PdfVectorIngestionService.java   # Interface
    └── impl/
        └── PdfVectorIngestionServiceImpl.java  # Full pipeline: extract → chunk → embed → store → search

src/main/resources/
├── application.properties
└── sample-docs/
    └── java_notes_sample.pdf            # Sample PDF used in this experiment
```

---

## Configuration

Add the following to `application.properties` (or set as environment variables):

```properties
spring.ai.openai.api-key=${SECRET}
spring.ai.openai.chat.options.model=gpt-4.1-nano
spring.ai.openai.embedding.options.model=text-embedding-3-small
spring.ai.openai.embedding.options.dimensions=1536

spring.ai.vectorstore.pinecone.api-key=${PINECONE_API_KEY}
spring.ai.vectorstore.pinecone.index-name=${PINECONE_INDEX}

pdf.ingestion.namespace.pages=pdf-pages
pdf.ingestion.namespace.sections=pdf-sections
```

---

## Running the Project

```bash
./gradlew bootRun
```

- Base URL: `http://localhost:8080/spring-ai`
- Swagger UI: `http://localhost:8080/spring-ai/swagger-ui/index.html`

---

## API Endpoints

### Ingest — Strategy 1 (Flat Pages)
Reads each PDF page as raw text, chunks it, stores in `pdf-pages` namespace. No metadata.

```bash
curl -X POST "http://localhost:8080/spring-ai/pdf-ingestion/ingest/pages?fileName=java_notes_sample.pdf"
```

### Ingest — Strategy 2 (Sections)
Divides each page into zones, keeps body only, stores in `pdf-sections` namespace with `printedPageNumber` metadata.

```bash
curl -X POST "http://localhost:8080/spring-ai/pdf-ingestion/ingest/sections?fileName=java_notes_sample.pdf"
```

### Search — Pages Namespace
Retrieves top-K chunks from flat page text, calls OpenAI, returns the answer. No source page info available.

```bash
curl --location 'http://localhost:8080/spring-ai/pdf-ingestion/search/pages?question=What%20is%20Java%20programming%3F&topK=2'
```

### Search — Sections Namespace
Retrieves top-K body-only chunks, calls OpenAI, returns the answer and which pages it came from.

```bash
curl --location 'http://localhost:8080/spring-ai/pdf-ingestion/search/sections?question=What%20is%20Java%20programming%3F&topK=2'
```

---

## Comparing the Two Strategies

Hit both search endpoints with the same question and compare:

| | Pages Strategy | Sections Strategy |
|---|---|---|
| **Input to chunker** | Full raw page text including header/footer | Body text only |
| **Boilerplate in chunks** | Yes — college name, course title repeated | No — stripped before chunking |
| **Metadata** | None | `sourceFile`, `printedPageNumber` |
| **Response** | `answer` only | `answer` + `sourcePages` |
| **Citable source** | No | Yes — exact printed page number |

