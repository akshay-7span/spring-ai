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

## Setup

1. **Clone the repository**
   ```bash
   git clone <repo-url>
   cd spring-ai
   ```

2. **Add your OpenAI API key** in `src/main/resources/application.properties`:
   ```properties
   spring.ai.openai.api-key=YOUR_API_KEY
   ```

3. **Run the project**
   ```bash
   ./gradlew bootRun
   ```

## Access

- Base URL: `http://localhost:8080/spring-ai`
- Swagger UI: `http://localhost:8080/spring-ai/swagger-ui/index.html`

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