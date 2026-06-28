# Spring AI Learning Project

A hands-on, step-by-step playground for learning **[Spring AI](https://docs.spring.io/spring-ai/reference/)** with Java and OpenAI. Each topic is a small, self-contained project that demonstrates **one** concept — from a basic chat call all the way up to Retrieval-Augmented Generation (RAG), tool calling, agentic loops, and the Model Context Protocol (MCP).

If you are learning Spring AI and want runnable examples that build from the basics up to production-style patterns, this repository is for you.

---

## How This Repository Is Organized

Every concept lives on its **own branch** so you can study one idea at a time without noise from other features. Pick a topic below, check out that branch, and you'll find code plus a dedicated README explaining exactly what it does and how to test it.

```bash
git clone https://github.com/akshay-7span/spring-ai.git
cd spring-ai
git checkout AI-1   # or any topic branch from the table below
```

Each branch's README includes setup steps, `curl` examples, and sample responses, so you can run and verify it on its own.

---

## Learning Path

Work through them top to bottom — each concept builds naturally on the previous one.

| # | Branch | Topic | What you'll learn |
|---|--------|-------|-------------------|
| 0 | [`AI-0`](https://github.com/akshay-7span/spring-ai/tree/AI-0) | **Project Setup** | The base Spring Boot + Spring AI scaffold every other topic starts from |
| 1 | [`AI-1`](https://github.com/akshay-7span/spring-ai/tree/AI-1) | **Simple Chat** | Send a prompt to OpenAI and get a response using Spring AI's `ChatClient` |
| 2 | [`AI-2`](https://github.com/akshay-7span/spring-ai/tree/AI-2) | **Output Parsing** | Convert raw LLM text into structured `List`, `Map`, and Java Bean objects |
| 3 | [`AI-3`](https://github.com/akshay-7span/spring-ai/tree/AI-3) | **Prompt Stuffing** | Inject your own data (e.g. a meeting transcript) into prompts to ground answers |
| 4 | [`AI-4`](https://github.com/akshay-7span/spring-ai/tree/AI-4) | **RAG with Local Vector Store** | Build a Retrieval-Augmented Generation pipeline using an in-memory `SimpleVectorStore` |
| 5 | [`AI-5`](https://github.com/akshay-7span/spring-ai/tree/AI-5) | **RAG with Pinecone** | Move the same RAG pipeline onto a managed **Pinecone** vector database |
| 6 | [`AI-6`](https://github.com/akshay-7span/spring-ai/tree/AI-6) | **Function / Tool Calling** | Let the LLM call your Java methods (`@Tool`) to fetch live data — e.g. real-time weather |
| 7 | [`AI-7`](https://github.com/akshay-7span/spring-ai/tree/AI-7) | **Agentic Loop** | Drive a manual multi-step reasoning loop where the LLM chains several tool calls in sequence |
| 8 | [`AI-8`](https://github.com/akshay-7span/spring-ai/tree/AI-8) | **PDF Ingestion Quality** | Compare PDF parsing strategies and see how cleaner chunks produce better RAG answers |
| 9 | [`AI-9`](https://github.com/akshay-7span/spring-ai/tree/AI-9) | **MCP Server** | Expose your Spring AI tools as a **Model Context Protocol** server so any MCP client can use them |

> This list grows as new practices are added. Check back for more topics.

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.5.14**
- **Spring AI 1.0.8**
- **OpenAI** (`gpt-4.1-nano` for most topics; `gpt-4o` where multi-step reasoning is required)
- **Pinecone** (for the managed vector-store topics)

---

## Prerequisites

- Java 21+
- Gradle 8.8+
- An OpenAI API key
- A Pinecone account (only needed for the Pinecone-based topics)

---

## Quick Start

1. **Clone and pick a topic**
   ```bash
   git clone https://github.com/akshay-7span/spring-ai.git
   cd spring-ai
   git checkout AI-1
   ```

2. **Add your OpenAI API key** in `src/main/resources/application.properties`:
   ```properties
   spring.ai.openai.api-key=YOUR_API_KEY
   ```

3. **Run it**
   ```bash
   ./gradlew bootRun
   ```

4. **Access the app**
   - Base URL: `http://localhost:8080/spring-ai`
   - Swagger UI: `http://localhost:8080/spring-ai/swagger-ui/index.html`

Then follow the `curl` examples in that branch's own README to try it out.

---

## Where to Start

New to Spring AI? Begin with [`AI-1`](https://github.com/akshay-7span/spring-ai/tree/AI-1) and work your way down the table. Each branch is bite-sized and independently runnable.