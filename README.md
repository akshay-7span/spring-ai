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

This branch (`AI-1`) demonstrates practical integration of ChatGPT using the ChatGPT API.  
It contains code and examples for calling ChatGPT with the ChatGPT API.

## Implemented Features

- Integration with OpenAI's ChatGPT API using Spring AI.
- REST endpoint to interact with ChatGPT via `/chat?message=YourPrompt`.
- Example usage and configuration for API key management.

## How to Test

1. Start the application as described above.
2. Use the following CURL command to test the ChatGPT endpoint:
   ```bash
   curl --location 'localhost:8080/chat?message=SamplePrompt'

