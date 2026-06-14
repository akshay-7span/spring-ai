# Spring AI Learning Project

A hands-on learning project exploring Spring AI features using Java 21, Spring Boot 3.5, and OpenAI.

## Tech Stack

- Java 21
- Spring Boot 3.5.14
- Spring AI 1.0.8
- OpenAI (gpt-4o)

## Prerequisites

- Java 21+
- Gradle 8.8+
- OpenAI API key
- Pinecone account (free tier works)

## Setup

1. **Clone the repository**
   ```bash
   git clone <repo-url>
   cd spring-ai
   ```

2. **Export environment variables**
   ```bash
   export SECRET=<your-openai-api-key>
   export PINECONE_API_KEY=<your-pinecone-api-key>
   export PINECONE_INDEX=<your-pinecone-index-name>
   ```

3. **Create a Pinecone index** with:
   - Dimensions: `1536`
   - Metric: `cosine`

4. **Run the project**
   ```bash
   ./gradlew bootRun
   ```

## Access

- Base URL: `http://localhost:8080/spring-ai`
- Swagger UI: `http://localhost:8080/spring-ai/swagger-ui/index.html`

---

## Branch: AI-7 — Agentic Loop (RAG + Multi-Tool Sequential Reasoning)

### What does this branch demonstrate?

In AI-6, the LLM made a **fixed round-trip**: receive question → call one tool → get result → answer.
The number of LLM calls was always the same regardless of the question.

This branch introduces the **Agentic Loop** — a pattern where the LLM decides at each iteration whether it has enough data to answer or needs to call another tool. The loop runs until the LLM produces a final plain-text answer with no further tool requests.

This enables **genuine multi-step reasoning**: the LLM can chain tool calls where the output of one call determines the input of the next — something that cannot be collapsed into a single batch of parallel calls.

### Why is this useful?

- Handles questions that require multiple sequential data lookups
- The LLM autonomously decides which tools to call and in what order
- Enables building AI systems that reason, not just retrieve
- Foundation for production AI agents that can take actions in the real world

### How the agentic loop works

```
User question
     ↓
STEP 1  RAG — retrieve relevant meeting context from Pinecone
     ↓
STEP 2  Build prompt — inject RAG chunks + user question
     ↓
STEP 3  Register tools — start agentic loop

  ┌─ ITERATION 1 ───────────────────────────────────────────────┐
  │  LLM call → finish_reason: tool_calls                       │
  │  LLM requests: getTeamProjectAllocations()                  │
  │  → execute → append result to conversation history          │
  └─────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 2 ───────────────────────────────────────────────┐
  │  LLM now knows who is free; needs their skills              │
  │  LLM requests: getEmployeeSkillProfile("Rahul Desai")       │
  │  → execute → append result                                  │
  └─────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 3 ───────────────────────────────────────────────┐
  │  LLM cross-checks for leave conflicts                       │
  │  LLM requests: getTeamLeaveRecords()                        │
  │  → execute → append result                                  │
  └─────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 4 ───────────────────────────────────────────────┐
  │  LLM records its recommendation                             │
  │  LLM requests: draftResourceRecommendation(...)             │
  │  → execute → append confirmation                            │
  └─────────────────────────────────────────────────────────────┘
  ┌─ FINAL ─────────────────────────────────────────────────────┐
  │  LLM call → finish_reason: stop                             │
  │  LLM produces plain-text final answer — loop exits          │
  └─────────────────────────────────────────────────────────────┘
```

### READ tools vs ACTION tool

This branch uses four tools that fall into two categories:

| Tool | Type | What it does |
|------|------|--------------|
| `getTeamProjectAllocations()` | READ | Returns who is allocated, at what %, and until when |
| `getTeamLeaveRecords()` | READ | Returns approved leave for each team member |
| `getEmployeeSkillProfile(name)` | READ (parameterised) | Returns skills for a specific employee by name |
| `draftResourceRecommendation(...)` | ACTION | Records the final recommendation — requires data from all three READ tools |

The `draftResourceRecommendation` tool is the **forcing function** of the loop. Its four parameters (`employeeName`, `availableFrom`, `matchedSkills`, `leaveConflicts`) can only come from the results of the read tools. The LLM cannot call it in isolation — it is forced to gather all data first through sequential iterations.

### Key implementation detail — `internalToolExecutionEnabled(false)`

By default, Spring AI intercepts tool-call responses from the LLM, executes the tools internally, and returns only the final answer. This is the same behaviour as AI-6.

Here, `internalToolExecutionEnabled(false)` disables that auto-execution. Each raw LLM response is returned directly so we can:
- Log every iteration explicitly
- Execute tools ourselves
- Control the conversation history
- Drive the loop manually

### Why `gpt-4o` instead of `gpt-4.1-nano`?

Smaller models tend to shortcut — they either answer early without calling tools, or batch all tool calls into a single iteration regardless of data dependencies. `gpt-4o` reliably follows the sequential reasoning pattern this example requires.

### Implemented Classes

| Class | Role |
|-------|------|
| `RagConfiguration` | Pinecone vector store bean + meeting document ingestion on startup |
| `EmployeeDataService` | In-memory mock for three external systems: HRMS, project allocation, HR profiles |
| `EmployeeTools` | Four `@Tool`-annotated methods exposed to the LLM |
| `MeetingServiceImpl` | Manual agentic loop using `ChatModel` directly with `internalToolExecutionEnabled(false)` |
| `MeetingController` | `GET /meeting?question=...` endpoint |

### How to Test

**Meeting content question** (answered from RAG, no tools needed):
```bash
curl --location 'http://localhost:8080/spring-ai/meeting?question=What+is+the+go-live+date+of+the+project?'

curl --location 'http://localhost:8080/spring-ai/meeting?question=What+tech+stack+is+being+used?'
```

**Resource availability question** (triggers the full agentic loop — 4-5 iterations):
```bash
curl --location 'http://localhost:8080/spring-ai/meeting?question=Who+should+we+assign+for+the+Phase+2+backend+work+in+April+2026?'
```

Watch the logs — you will see each iteration of the agentic loop, every tool call the LLM requests, and the tool results fed back before the final answer is produced.
