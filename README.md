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