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

## Branch: AI-1 — Simple Chat Generation

Demonstrates basic integration with OpenAI's ChatGPT using Spring AI's `ChatClient`.

### Implemented Features

- REST endpoint to send a message to ChatGPT and get a response.

### How to Test

```bash
curl --location 'http://localhost:8080/spring-ai/chat?message=Hello'
```