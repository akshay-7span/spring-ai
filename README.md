# Spring AI Learning Project

This project is a learning exercise for integrating Spring AI with Java 21, Spring Boot 3.2, and OpenAI.

## Prerequisites

- Java 21
- Gradle 8.8
- Spring Boot 3.2

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

- Branch: `AI-4`
- Implemented: Example RAG integration with Spring AI:
  - Uses `SimpleVectorStore` to store embeddings.
  - On first run the app reads meeting text from `src/main/resources/meetings/project_kickoff_meeting.txt` using `TextReader`, splits text with `TokenTextSplitter`, computes embeddings via the configured `EmbeddingModel`, and persists the store to `src/main/resources/data/meeting-vector-store.json`.
  - On subsequent runs the persisted JSON file is loaded to avoid recomputing embeddings.

How to test
- Make sure your ChatGPT API key is set in `src/main/resources/application.properties`.
- First run will create the vector store file; delete `src/main/resources/data/meeting-vector-store.json` to force a rebuild.
- Query the meeting API (example):
  - `curl --location 'http://localhost:8080/spring-ai/meeting?question=Who%20are%20the%20participants%20of%20the%20meeting%3F'`

