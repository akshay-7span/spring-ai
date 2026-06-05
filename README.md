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

## Branch: AI-2 — Output Parsing

### What does this branch demonstrate?

By default, ChatGPT returns responses as plain unstructured text. While that works for simple use cases, real applications often need AI responses in a specific format — a list, a key-value map, or a typed Java object — so the data can be used directly in code without manual parsing.

This branch demonstrates how Spring AI's **Output Converters** solve this problem. They work by:
1. Automatically appending format instructions to your prompt so the AI responds in the expected format
2. Parsing the AI's response into the target Java type

### Why is this useful?

- No manual string parsing or regex
- Type-safe responses you can use directly in your application
- Consistent, predictable output from the AI

### Implemented Features

| Converter | Endpoint | Returns |
|-----------|----------|---------|
| `ListOutputConverter` | `GET /output-parser/list` | `List<String>` |
| `MapOutputConverter` | `GET /output-parser/map` | `Map<String, Object>` |
| `BeanOutputConverter` | `GET /output-parser/bean` | `JavaVersion` record |

### How to Test

```bash
# List — returns a JSON array of strings
curl --location 'http://localhost:8080/spring-ai/output-parser/list?prompt=List+5+programming+languages'

# Map — returns a JSON object with key-value pairs
curl --location 'http://localhost:8080/spring-ai/output-parser/map?prompt=Give+me+name+and+age+of+a+fictional+person'

# Bean — returns a typed Java record as JSON
curl --location 'http://localhost:8080/spring-ai/output-parser/bean?prompt=Tell+me+about+Java+21'
```