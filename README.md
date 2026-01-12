# Spring AI Learning Project

This project is a learning exercise for integrating Spring AI with Java 21, Spring Boot 3.2, and OpenAI.

## Prerequisites

- Java 21
- Gradle 8.8
- Spring Boot 3.2
- Pinecone account (for vector database) https://www.pinecone.io/

## Setup & Running

1. **Clone the repository**
2. **Build and run the project:**
   ```bash
   ./gradlew clean build
   ./gradlew bootRun
3. **Add your ChatGPT API key to `src/main/resources/application.properties`:**

## Access the application:
Application runs on: 
```bash 
    http://localhost:8080/spring-ai
``` 
Swagger UI:
```bash
    http://localhost:8080/spring-ai/swagger-ui/index.html
```

## Branch Information

- Branch: `AI-5`
- Implemented: Example RAG integration with Spring AI using `Pinecone` DB:
- Creates embeddings with the configured `EmbeddingModel` (OpenAI) and upserts/queries vectors in a `Pinecone` index via a `PineconeVectorStore` binding.
- On first run the app reads meeting text from `src/main/resources/meetings/project_kickoff_meeting.txt` using `TextReader`, splits text with `TokenTextSplitter`, computes embeddings and stores them in the configured Pinecone index; subsequent runs fetch vectors from Pinecone to avoid recomputing embeddings.
- Prerequisite: create a Pinecone account and set your Pinecone API key, environment, and index name in `src/main/resources/application.properties` before running the app.


How to test
- Make sure your ChatGPT API key is set in `src/main/resources/application.properties`.
- Query the meeting API (example):
  - `curl --location 'http://localhost:8080/spring-ai/meeting?question=Who%20are%20the%20participants%20of%20the%20meeting%3F'`

