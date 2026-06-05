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

## Branch: AI-6 — Function Calling (Tool Calling)

### What does this branch demonstrate?

By default, an LLM only knows what it was trained on — it has no access to live data like current weather, stock prices, or your database. **Function Calling** (also called Tool Calling) solves this by letting the LLM request external data at runtime.

This branch demonstrates how Spring AI's `@Tool` annotation wires a Java method as a callable tool. When the user asks about the weather, the LLM detects it needs live data, requests the `getCurrentWeather` tool, Spring AI calls the method automatically, and the result is fed back to the LLM to compose the final answer.

### Why is this useful?

- Gives the LLM access to real-time, live data it was never trained on
- The LLM decides **when** to call the tool — you don't hardcode the logic
- Works with any external API, database, or service — just annotate a method with `@Tool`
- Foundation for building AI agents that can take actions in the real world

### How it works

```
User question
     ↓
LLM receives question + tool definition
     ↓
LLM decides: "I need live weather data" → requests getCurrentWeather("Mumbai")
     ↓
Spring AI calls WeatherTools.getCurrentWeather("Mumbai") automatically
     ↓
Tool fetches data from Open-Meteo API (free, no API key needed)
     ↓
Tool result is fed back to the LLM
     ↓
LLM composes a friendly final answer
```

### Implemented Features

| Class | Role |
|-------|------|
| `WeatherTools` | `@Tool`-annotated method that fetches live weather from Open-Meteo |
| `WeatherServiceImpl` | Registers the tool with `ChatClient` via `.tools(weatherTools)` |
| `WeatherController` | `GET /weather?question=...` endpoint |

### How to Test

```bash
curl --location 'http://localhost:8080/spring-ai/weather?question=What+is+the+weather+in+Mumbai?'

curl --location 'http://localhost:8080/spring-ai/weather?question=Is+it+raining+in+London+right+now?'

curl --location 'http://localhost:8080/spring-ai/weather?question=What+should+I+wear+in+Tokyo+today?'
```

> The weather data is fetched live from [Open-Meteo](https://open-meteo.com/) — a free, no-key-required weather API.