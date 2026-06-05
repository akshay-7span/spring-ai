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