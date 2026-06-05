# Spring AI Learning Project

A hands-on learning project exploring Spring AI features using Java 21, Spring Boot 3.5, and OpenAI.

## Tech Stack

- Java 21
- Spring Boot 3.5.14
- Spring AI 1.0.8
- OpenAI (gpt-4.1-nano)

## Prerequisites

- Java 21+
- Gradle 8.8+
- OpenAI API key
- Pinecone account (free tier works)

## Setup

1. **Clone the repository**
   ```bash
   git clone <repo-url>
   cd spring-ai
   ```

2. **Add your API keys** in `src/main/resources/application.properties`:
   ```properties
   spring.ai.openai.api-key=YOUR_OPENAI_API_KEY
   spring.ai.vectorstore.pinecone.api-key=YOUR_PINECONE_API_KEY
   spring.ai.vectorstore.pinecone.index-name=YOUR_INDEX_NAME
   ```

3. **Create a Pinecone index** with:
   - Dimensions: `1536`
   - Metric: `cosine`

4. **Run the project**
   ```bash
   ./gradlew bootRun
   ```

## Access

- Base URL: `http://localhost:8080/spring-ai`
- Swagger UI: `http://localhost:8080/spring-ai/swagger-ui/index.html`

---

## Branch: AI-3 — Prompt Stuffing

### What does this branch demonstrate?

**Prompt Stuffing** is a technique where you include the full context (e.g. a document or transcript) directly inside the prompt sent to the AI. Instead of the AI relying on its training data, it is forced to answer based only on the content you provide.

This branch demonstrates prompt stuffing using meeting transcripts. The full transcript is loaded into memory and injected into the prompt so the AI can answer questions about a specific meeting.

### Why is this useful?

- Lets you query private or domain-specific documents that the AI has never seen
- Ensures answers are grounded in your data, not general AI knowledge
- Simple to implement — no vector store or embeddings required
- Works well for small documents that fit within the model's context window

### Available Meetings

| Meeting ID | Title |
|------------|-------|
| `meeting1` | Daily Stand-up |
| `meeting2` | Project Scope of Work — TaskFlow |
| `meeting3` | Sprint 4 Retrospective |

### How to Test

```bash
curl --location 'http://localhost:8080/spring-ai/meeting?meetingId=meeting1&question=What+are+the+action+items?'

curl --location 'http://localhost:8080/spring-ai/meeting?meetingId=meeting2&question=What+is+the+MVP+delivery+date?'

curl --location 'http://localhost:8080/spring-ai/meeting?meetingId=meeting3&question=What+decisions+were+made?'
```

---

## Branch: AI-4 — RAG with SimpleVectorStore

### What does this branch demonstrate?

**Retrieval-Augmented Generation (RAG)** is a technique that improves AI responses by first retrieving relevant information from a knowledge base and then passing only that context to the AI. Unlike Prompt Stuffing (AI-3), RAG does not send the entire document — it splits the document into chunks, stores them as vector embeddings, and retrieves only the most relevant chunks for each question.

This branch uses Spring AI's `SimpleVectorStore` — an in-memory vector store — to index a project kickoff meeting transcript and answer questions about it.

### Why is this useful?

- Handles large documents that exceed the model's context window limit
- More efficient — only relevant content is sent to the AI, reducing token usage
- Scales to multiple documents without increasing prompt size
- Foundation for production RAG systems (swap `SimpleVectorStore` with Pinecone, Redis, etc.)

### How it works

1. On first run, the meeting transcript is read, split into chunks, embedded, and saved to a JSON file
2. On subsequent runs, the saved vector store is loaded from disk (no re-embedding)
3. At query time, the question is embedded and the top matching chunks are retrieved
4. The retrieved chunks are injected into the prompt and sent to the AI

### How to Test

```bash
curl --location 'http://localhost:8080/spring-ai/rag-meeting?question=What+is+the+go-live+date+of+the+project?'

curl --location 'http://localhost:8080/spring-ai/rag-meeting?question=Who+are+the+participants+of+the+meeting?'
```

---

## Branch: AI-5 — RAG with Pinecone

### What does this branch demonstrate?

This branch builds on the RAG concept introduced in AI-4, replacing the in-memory `SimpleVectorStore` with **Pinecone** — a fully managed, cloud-hosted vector database.

In AI-4, embeddings were stored in memory and lost on every restart. Here, embeddings are persisted in Pinecone, so the knowledge base survives restarts and can scale to millions of documents without running out of memory.

### Why is Pinecone better than SimpleVectorStore?

| Feature           | SimpleVectorStore (AI-4)    | Pinecone (AI-5)             |
|-------------------|-----------------------------|-----------------------------|
| Storage           | In-memory (lost on restart) | Persistent cloud storage    |
| Scalability       | Limited by RAM              | Scales to billions of vectors |
| Re-embedding      | Every restart               | One-time ingestion          |
| Production-ready  | No                          | Yes                         |

### How it works

1. On startup, the meeting transcript is read, split into chunks, embedded, and **uploaded to Pinecone**
2. At query time, the question is embedded and the **top 3 most similar chunks** are fetched from Pinecone
3. The retrieved chunks are injected into the prompt and sent to the AI

> **Note:** In this demo, documents are re-ingested on every startup. In production, you would add a check to skip ingestion if documents are already present in the index.

### How to Test

```bash
curl --location 'http://localhost:8080/spring-ai/pinecone-rag-meeting?question=What+is+the+go-live+date+of+the+project?'

curl --location 'http://localhost:8080/spring-ai/pinecone-rag-meeting?question=Who+are+the+participants+of+the+meeting?'

curl --location 'http://localhost:8080/spring-ai/pinecone-rag-meeting?question=What+is+the+tech+stack+used?'
```