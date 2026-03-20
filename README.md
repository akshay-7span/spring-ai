# Spring AI Learning Project

This project is a step-by-step learning exercise for integrating **Spring AI** with Java 21, Spring Boot 3.2, and OpenAI.
Each branch builds on the previous one, introducing a new Spring AI concept.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 |
| Gradle | 8.8 |
| Spring Boot | 3.2 |
| Spring AI | 1.0.0 |
| OpenAI account | — |
| Pinecone account | — |

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
| `AI-7` | RAG + Agentic Loop — multi-step autonomous reasoning with READ and ACTION tools |

---

## Current Branch — `AI-7`: RAG + Agentic Loop

### What is an Agentic Loop?

In the previous branch (AI-6), the LLM made **one fixed round-trip**: call tools → get results → answer.
The number of LLM calls was always the same regardless of the question.

An **agentic loop** removes that constraint. The LLM decides at each iteration whether it needs more data or is ready to answer:

```
LLM call → needs more data? → execute tools → LLM call → needs more data? → ... → final answer
```

Each iteration the LLM reasons over what it learned and decides what to do next.
The loop exits naturally when the LLM produces a plain-text answer with no tool calls.

### What makes it genuinely sequential — READ vs ACTION tools

The key insight is the difference between two types of tools:

| Type | Purpose | Example |
|---|---|---|
| **READ tool** | Fetches data. LLM can call multiple read tools in parallel. | `getTeamProjectAllocations()` |
| **ACTION tool** | Performs a finalising action. Its parameters can only be filled after reads are done. | `draftResourceRecommendation(name, availableFrom, skills, leaveConflicts)` |

The action tool is the **forcing function**. Its parameters (`name`, `availableFrom`, `matchedSkills`, `leaveConflicts`) can only be known after the read tools have been called and reasoned about. The LLM cannot skip or batch the reads — it must gather the data first.

### Why model choice matters

Agentic behaviour requires a model capable of multi-step reasoning and reliable tool call decisions.
`gpt-4o` is used for this branch. Smaller models (e.g. `gpt-4.1-nano`) tend to shortcut —
answering early with incomplete data or batching all tool calls into one iteration regardless of dependencies.

### Tool description best practice

`@Tool` descriptions follow one rule: **describe what the tool returns, not when or how to use it**.
The LLM reads the description to understand the data available, then decides autonomously.
Reasoning logic and sequencing instructions do not belong in tool descriptions — they belong in the system prompt or, better, emerge naturally from data dependencies between tools.

---

### What was implemented

#### 1. `EmployeeDataService` — mock external systems

Acts as a proxy for three real-world external systems:

| System | Data returned |
|---|---|
| HRMS / Leave Management | Approved leave records per employee (name, type, start date, end date) |
| Project Allocation System | Resource allocation per employee (name, role, project, allocation %, start/end dates) |
| HR Skill / Profile DB | Skill profile per employee (role, primary technology, skills list, years of experience) |

Data is static in-memory so the focus stays on the AI mechanics, not the data layer.

#### 2. `EmployeeTools` — four `@Tool` definitions

| Tool | Type | Parameters | What it returns |
|---|---|---|---|
| `getTeamLeaveRecords()` | READ | none | All approved leave records for the team |
| `getTeamProjectAllocations()` | READ | none | All project allocation records for the team |
| `getEmployeeSkillProfile(employeeName)` | READ | employee name | Skills, role, and experience for one employee |
| `draftResourceRecommendation(...)` | ACTION | name, availableFrom, matchedSkills, leaveConflicts | Confirmation that the recommendation was recorded |

`getEmployeeSkillProfile` is parameterised — the LLM must supply an employee name it discovered from `getTeamProjectAllocations`. This creates the first sequential dependency.

`draftResourceRecommendation` is the action tool — all four of its parameters come from prior tool results, making it the forcing function that ensures the full investigation is completed before a recommendation is made.

#### 3. `MeetingServiceImpl` — agentic loop

Uses `ChatModel` directly (instead of `ChatClient`) for full manual control over each iteration.

**Key design decisions:**

| Decision | Reason |
|---|---|
| `internalToolExecutionEnabled(false)` | Disables Spring AI's silent auto-execution so we control each iteration and can log every step |
| `MAX_ITERATIONS = 10` | Safety guard — prevents runaway loops if the LLM keeps requesting tools unexpectedly |
| Full message history on every call | OpenAI's API is stateless; the complete `user → assistant → tool → assistant → ...` history must be re-sent each iteration |
| `AssistantMessage` added before tool results | OpenAI requires the assistant turn (containing tool-call requests) to precede the tool-result messages in history |

---

### Agentic loop flow

```
STEP 1   RAG — similarity search against Pinecone (top-3 chunks from meeting transcript)
STEP 2   Build prompt — inject retrieved chunks as {context} + user {question}
STEP 3   Register tools — MethodToolCallbackProvider scans @Tool methods; agentic loop starts

  ┌─ ITERATION 1 ──────────────────────────────────────────────────────────┐
  │  LLM call → finish_reason: TOOL_CALLS                                  │
  │  LLM requests: getTeamProjectAllocations()                             │
  │  → execute → feed results back                                         │
  └────────────────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 2 ──────────────────────────────────────────────────────────┐
  │  LLM call → finish_reason: TOOL_CALLS                                  │
  │  LLM now knows who is free; checks their skills                        │
  │  LLM requests: getEmployeeSkillProfile("Arjun Mehta")                  │
  │               getEmployeeSkillProfile("Meena Raval") ...               │
  │  → execute → feed results back                                         │
  └────────────────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 3 ──────────────────────────────────────────────────────────┐
  │  LLM call → finish_reason: TOOL_CALLS                                  │
  │  LLM has shortlisted candidates; confirms no leave conflicts           │
  │  LLM requests: getTeamLeaveRecords()                                   │
  │  → execute → feed results back                                         │
  └────────────────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 4 ──────────────────────────────────────────────────────────┐
  │  LLM call → finish_reason: TOOL_CALLS                                  │
  │  LLM has all data; records the recommendation                          │
  │  LLM requests: draftResourceRecommendation("Arjun Mehta", ...)         │
  │  → execute → feed confirmation back                                    │
  └────────────────────────────────────────────────────────────────────────┘
  ┌─ ITERATION 5 ──────────────────────────────────────────────────────────┐
  │  LLM call → finish_reason: STOP                                        │
  │  No tool calls — LLM produces final answer                             │
  │  LOOP COMPLETE                                                          │
  └────────────────────────────────────────────────────────────────────────┘
```

---

### How to test

Make sure both API keys are set and the app is running.

#### Question answered from RAG only — no tool calls, loop exits in iteration 1

```bash
curl --location 'http://localhost:8080/spring-ai/meeting?question=What%20is%20the%20go-live%20date%20of%20the%20SolarVision%20project%3F'
```

The LLM answers directly from the meeting transcript. `finish_reason: STOP` on iteration 1.

#### Question that triggers the full agentic loop

```bash
curl --location 'http://localhost:8080/spring-ai/meeting?question=Who%20should%20we%20assign%20for%20the%20Phase%202%20backend%20work%20in%20April%202026%3F'
```

The LLM works through 4–5 iterations: checking allocations, verifying skills, confirming no leave conflicts, drafting the recommendation, then producing the final answer.

---

### What you will see in the logs

```
========================================================================
STEP 1 : Retrieving relevant context from Pinecone (RAG)
========================================================================
STEP 2 : Final prompt constructed
========================================================================
STEP 3 : Tools registered — starting agentic loop (max 10 iterations)
         Tools available to LLM:
           - getTeamProjectAllocations  |  Fetches all employee project...
           - getTeamLeaveRecords        |  Fetches all approved employee...
           - getEmployeeSkillProfile    |  Fetches the skill profile for...
           - draftResourceRecommendation|  Records a resource recommendation...
========================================================================
AGENTIC LOOP — Iteration 1 / 10  |  Sending request to LLM
               Conversation history : 1 message(s) in context
========================================================================
AGENTIC LOOP — Iteration 1  |  LLM response received
               Finish reason : TOOL_CALLS
               LLM requested 1 tool call(s) — will execute and loop back
               Tool      : getTeamProjectAllocations
               Arguments : {}
========================================================================
AGENTIC LOOP — Iteration 1  |  Executing 1 tool(s)
               Tool       : getTeamProjectAllocations
               Result     : [{"employeeName":"Ravi Shah",...}]
               → Proceeding to Iteration 2
========================================================================
AGENTIC LOOP — Iteration 2 / 10  |  Sending request to LLM
               Conversation history : 3 message(s) in context
...
========================================================================
AGENTIC LOOP — Iteration 4  |  LLM response received
               Finish reason : TOOL_CALLS
               Tool      : draftResourceRecommendation
               Arguments : {"employeeName":"Arjun Mehta","availableFrom":"2026-04-01",...}
========================================================================
AGENTIC LOOP — COMPLETE  |  Final answer produced after 5 iteration(s)
------------------------------------------------------------------------
FINAL ANSWER :
------------------------------------------------------------------------
Based on the Phase 2 requirements for Java and Spring Boot backend skills...
========================================================================
```