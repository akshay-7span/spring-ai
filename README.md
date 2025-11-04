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
   
   This branch (`AI-3`) demonstrates prompt stuffing techniques and defensive handling. 
   It contains examples showing how meeting data can be embedded into prompts and how the service extracts follow-up questions, summaries, and action items.
   
   ## Implemented Features
   
   - Demonstrates prompt-stuffing.
   - Meeting ingestion: parse and normalize meetingId and transcript.
   
    Sample questions for Meeting 1:
    1.	What task did Alice plan to start working on after completing the user registration API?
	2.	Which feature’s UI bug did John mention, and when was it expected to be fixed?
	3.	When will QA testing for the dashboard begin?
	4.	Who was responsible for preparing the dashboard test plan?
	5.	What reminder did Tom give the team regarding Jira tasks?

    Sample questions for Meeting 2:
    1.	What is the main objective of the TaskFlow Automation Platform?
	2.	Which modules were finalized for Phase 1 of the project?
	3.	What was the decided technology stack?
	4.	When is the MVP expected to be delivered?
	5.	Which components were deferred to the next phase?
	6.	Who was responsible for finalizing the architecture document?

    Sample questions for Meeting 3:
    1.	What improvements were suggested regarding mid-sprint design changes?
	2.	What aspect did Maya identify as needing better documentation?
	3.	What was Alice’s contribution plan for the next sprint?
	4.	Who is responsible for drafting the change request workflow?
	5.	What positive aspects did the team highlight from this sprint?
   
   ## How to Test
   
   1. Start the application as described above.
   2. Use the following cURL example to analyze a meeting (replace payload fields):
   ```bash
   curl --location 'localhost:8080/spring-ai/meeting?meetingId=meeting2&question=What%20was%20the%20decided%20technology%20stack%3F''

