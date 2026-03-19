# Spring AI Learning Project

This project is a step-by-step learning exercise for integrating **Spring AI** with Java 21, Spring Boot 3.2, and OpenAI.
Each branch builds on the previous one, introducing a new Spring AI concept.

---

## Prerequisites

| Requirement | Version |
|---|-|
| Java | 21 |
| Gradle | 8.8 |
| Spring Boot | 3.2 |
| Spring AI | 1.0.0 |
| OpenAI account |
| Pinecone account |

---

## Setup & Running

1. **Clone the repository**
   ```bash
   git clone <repo-url>
   cd spring-ai
   ```

2. **Set API keys in `src/main/resources/application.properties`:**
   ```properties
   spring.ai.openai.api-key=${SECRET}
   spring.ai.vectorstore.pinecone.api-key=${PINECONE_API_KEY}
   ```
   Export the environment variables before running:
   ```bash
   export SECRET=<your-openai-api-key>
   export PINECONE_API_KEY=<your-pinecone-api-key>
   ```

3. **Build and run:**
   ```bash
   ./gradlew clean build
   ./gradlew bootRun
   ```

## Access the application

| Interface | URL |
|---|---|
| API base | `http://localhost:8080/spring-ai` |
| Swagger UI | `http://localhost:8080/spring-ai/swagger-ui/index.html` |

---

## Branch History

| Branch | Concept |
|---|---|
| `AI-1` | Simple chat generation with a system instruction |
| `AI-3` | Prompt stuffing — injecting context directly into the prompt |
| `AI-4` | RAG with `SimpleVectorStore` (in-memory) |
| `AI-5` | RAG with `PineconeVectorStore` (persistent vector DB) |
| `AI-6` | RAG + Tool Calling — LLM-driven function execution with manual round-trip control |

---

## Current Branch — `AI-6`: RAG + Tool Calling

### What is Tool Calling?

Tool calling (also called function calling) lets the LLM decide at runtime whether it needs external data to answer a question.
Instead of cramming all possible data into the prompt upfront, you register tools (Java methods) with the LLM.
When the LLM determines it needs data, it emits a structured tool-call request. Your application executes the method, returns the result, and the LLM uses that data to produce its final answer.

This branch combines **RAG** (for meeting transcript retrieval) and **Tool Calling** (for live employee data) in a single request flow.

---

### What was implemented

#### 1. `EmployeeDataService` — mock external systems

Acts as a proxy for two real-world external systems that would typically be REST APIs or database queries:

- **HRMS / Leave Management System** — returns approved leave records per employee (name, leave type, start date, end date)
- **Project Allocation System** — returns resource allocation records per employee (name, role, project, allocation %, start/end dates)

Data is static in-memory so the focus stays on the AI mechanics, not the data layer.

#### 2. `EmployeeTools` — Spring AI `@Tool` definitions

Each `@Tool`-annotated method becomes a callable tool available to the LLM:

| Tool method | What it does |
|---|---|
| `getTeamLeaveRecords()` | Fetches all approved employee leave records from the HRMS |
| `getTeamProjectAllocations()` | Fetches all employee project allocation records from the resource management system |

The `@Tool(description = "...")` text is sent to OpenAI as the tool's schema — this is how the LLM decides when to call each tool.
**No tool names are hardcoded in the prompt.** The `@Tool` descriptions are the single source of truth.

#### 3. `MeetingServiceImpl` — manual tool-calling round-trip

Uses `ChatModel` directly (instead of `ChatClient`) to retain full control over the conversation flow so every step can be observed and logged.

**Key Spring AI types used:**

| Type | Purpose |
|---|---|
| `MethodToolCallbackProvider` | Scans `EmployeeTools` for `@Tool` methods and produces one `ToolCallback` per method, each holding the tool definition and the Java method reference |
| `OpenAiChatOptions` | Per-request configuration attached to the `Prompt`; registers tool callbacks and disables Spring AI's internal auto-execution |
| `internalToolExecutionEnabled(false)` | Prevents Spring AI from silently auto-executing tools; the raw first LLM response (with tool-call requests) is returned to us so we can log it and execute tools manually |
| `AssistantMessage` | The LLM's first response (role: `assistant`); must be pushed back into the message history before sending tool results, because OpenAI's API is stateless and requires the full conversation history per call |
| `ToolResponseMessage` | Wraps the tool execution results (role: `tool`) and is appended to the message history before the second LLM call |

---

### Request flow (7 steps)

```
STEP 1  RAG — similarity search against Pinecone (top-3 chunks from meeting transcript)
STEP 2  Build prompt — inject retrieved chunks as {context} + user {question}
STEP 3  First LLM call — prompt + tool definitions sent to OpenAI
STEP 4  First LLM response — LLM either answers directly (no tools needed)
                             or emits tool-call requests
STEP 5  Tool execution — we invoke the requested @Tool methods and collect results
STEP 6  Second LLM call — original messages + assistant message + tool results sent back
STEP 7  Final LLM response — LLM reasons over context + tool data and answers
```

If the LLM can answer from the meeting transcript alone (STEP 4), STEP 5–7 are skipped entirely.

---

### How to test

Make sure both API keys are set and the app is running, then use one of the example requests below.

#### Question answered from RAG only (no tool calls)

```bash
curl --location 'http://localhost:8080/spring-ai/meeting?question=What%20is%20the%20go-live%20date%20of%20the%20SolarVision%20project%3F'
```

The LLM answers directly from the meeting transcript. Steps 5–7 are skipped.
You will see `"LLM answered directly from context — no tool calls were made."` in the logs.

#### Question that triggers tool calls (RAG + live data)

```bash
curl --location 'http://localhost:8080/spring-ai/meeting?question=Who%20is%20available%20for%20the%20Phase%202%20backend%20work%20in%20April%202026%3F'
```

The LLM recognises it needs live employee data, calls `getTeamLeaveRecords` and `getTeamProjectAllocations`, and combines the results with the meeting context to give a resource recommendation.
Watch the logs to see all 7 steps execute in sequence.

---

### What you will see in the logs

```
======================================================================
STEP 1 : Retrieving relevant context from Pinecone (RAG)
======================================================================
STEP 2 : Final prompt constructed
======================================================================
STEP 3 : Sending first call to LLM
         Tools registered and available to LLM:
           - getTeamLeaveRecords  |  Fetches all approved employee leave records...
           - getTeamProjectAllocations  |  Fetches all employee project allocation records...
======================================================================
STEP 4 : First LLM response received
         LLM did NOT answer yet — it requested 2 tool call(s):
         Tool name  : getTeamLeaveRecords
         Tool name  : getTeamProjectAllocations
======================================================================
STEP 5 : Executing tool(s) requested by LLM
         TOOL CALL : LLM requested → getTeamLeaveRecords()
         TOOL CALL : LLM requested → getTeamProjectAllocations()
======================================================================
STEP 6 : Sending second call to LLM with tool results attached
======================================================================
STEP 7 : Final LLM response received (LLM combined context + tool results)
======================================================================
```