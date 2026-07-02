# RAG Reranking Demo — Spring AI + Pinecone

This project shows **why reranking matters in Retrieval-Augmented Generation (RAG)**.

A vector database is great at finding chunks of text that are *semantically near* your
question, but "near in embedding space" is not the same as "actually answers the question."
The top vector hits often include loosely related passages, and the genuinely relevant chunk
can sit a few positions down the list. A **reranker** is a second, more precise model that
re-scores the retrieved chunks against the query and pushes the truly relevant ones to the top.

To make the difference visible, this app answers the **same question two ways** over the same
employee handbook and shows you exactly which chunks each path fed to the language model:

- **Without reranking** — keep the vector DB's raw similarity order, send the top 3 chunks to the LLM.
- **With reranking** — take the top 5 chunks, run them through a hosted reranker, send the best 3 to the LLM.

Each response includes a `chunksUsed` list so you can compare *which* chunks were selected and
in *what order* — the whole point of the demo.

## How it works

```
question ──► embed ──► Pinecone similarity search (Top-K = 5)
                                   │
              ┌────────────────────┴─────────────────────┐
   without reranking                              with reranking
   take first 3 (similarity order)        Pinecone rerank API (bge-reranker-v2-m3)
              │                                  take Top-N = 3 (relevance order)
              └────────────────────┬─────────────────────┘
                                   ▼
                         LLM answers using only those chunks
```

The vector search uses Spring AI's Pinecone vector store. The rerank step is a **separate**
call to Pinecone's hosted Inference Rerank API — the chunks are never reranked locally.

## Features

| Capability | Detail |
|---|---|
| Document ingestion | Splits `abc_company_employee_handbook.txt` into ~300-token chunks, embeds and stores them in Pinecone |
| Idempotent ingest | Re-running ingestion skips work if the data is already present |
| Plain retrieval | Top-K vector search in raw cosine-similarity order |
| Reranked retrieval | Top-K vector search, then a hosted reranker selects the Top-N most relevant |
| Transparent results | Every response lists the exact chunks used, with their scores |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/ingest` | Ingest the handbook into the vector store (run once) |
| `GET`  | `/api/search/without-reranking?query=...` | Answer using the top 3 chunks in similarity order |
| `GET`  | `/api/search/with-reranking?query=...` | Answer using the top 3 chunks after reranking |

> All paths are served under the context path `/spring-ai`, e.g.
> `http://localhost:8080/spring-ai/api/ingest`.

## Prerequisites

- Java 21
- An **OpenAI API key** (used for embeddings and the chat answer)
- A **Pinecone API key** and an existing **Pinecone index** whose **dimension is `1536`**
  (matching the `text-embedding-3-small` embedding model)

Provide them as environment variables before starting the app:

```bash
export SECRET=sk-your-openai-key
export PINECONE_API_KEY=your-pinecone-key
export PINECONE_INDEX=your-index-name
```

Chunks are stored in the `employee-handbook` namespace of your index.

## Running

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8080/spring-ai`.

## Try it

### 1. Ingest the handbook (run once)

```bash
curl -X POST http://localhost:8080/spring-ai/api/ingest
```

Sample response:

```json
{
  "message": "Ingestion complete",
  "totalChunksIngested": 42
}
```

Calling it again is safe — it detects existing data and skips:

```json
{
  "message": "Ingestion complete",
  "totalChunksIngested": 0
}
```

### 2. Ask a question — without reranking

```bash
curl "http://localhost:8080/spring-ai/api/search/without-reranking?query=What%20should%20I%20do%20if%20I%20fall%20sick%20while%20working%20from%20home%3F"
```

Sample response (truncated):

```json
{
  "query": "What should I do if I fall sick while working from home?",
  "answer": "Apply for sick leave for that day and notify your manager ...",
  "chunksUsed": [
    { "chunkIndex": 3, "content": "Every confirmed employee is entitled to 12 sick leaves per calendar year ...", "score": 0.46 },
    { "chunkIndex": 2, "content": "Sick leave during probation will be recorded and considered ...", "score": 0.42 },
    { "chunkIndex": 4, "content": "Sick leave cannot be combined with casual leave to extend a holiday ...", "score": 0.39 }
  ],
  "totalChunksRetrievedFromDB": 5,
  "totalChunksSentToLLM": 3
}
```

The `score` values here are **cosine similarity** — how close each chunk is to the question *in
meaning*. Notice the problem: all three top chunks are generic sick-leave passages that share the
words "sick" and "leave", but **none of them addresses the work-from-home scenario**. The chunk
that actually answers the question (about being sick *at home*) shares fewer keywords, so it sits
lower down and never makes the top 3.

### 3. Ask the same question — with reranking

```bash
curl "http://localhost:8080/spring-ai/api/search/with-reranking?query=What%20should%20I%20do%20if%20I%20fall%20sick%20while%20working%20from%20home%3F"
```

Sample response (truncated):

```json
{
  "query": "What should I do if I fall sick while working from home?",
  "answer": "If you fall sick while working from home, stop working, inform your manager, and apply for sick leave for that day on the HR portal ...",
  "chunksUsed": [
    { "chunkIndex": 6, "content": "If you are working from home and fall sick during the day, you must not continue working. Inform your manager and apply for sick leave ...", "score": 0.97 },
    { "chunkIndex": 3, "content": "Every confirmed employee is entitled to 12 sick leaves per calendar year ...", "score": 0.68 },
    { "chunkIndex": 2, "content": "Sick leave during probation will be recorded and considered ...", "score": 0.41 }
  ],
  "totalChunksRetrievedFromDB": 5,
  "totalChunksSentToLLM": 3
}
```

Now the `score` values are **rerank relevance** — how well each chunk *actually answers* the
question. The work-from-home chunk (`chunkIndex: 6`), which plain similarity ranked too low to
send to the LLM, has been pulled all the way to the top, and the generic sick-leave chunks are
demoted below it. That single reorder is the difference between an answer grounded in the right
passage and one that misses it.

### Watching the difference in the console

Every search also prints the chunks it retrieved to the application logs, so the difference is
easy to inspect (and screenshot) without reading the raw JSON. Ingestion prints nothing — only
the search endpoints log chunks.

- `/without-reranking` prints the chunks in vector-similarity order.
- `/with-reranking` prints **two** blocks: the Top-K candidates *before* reranking (scored by
  **Similarity**), and the Top-N *after* reranking (scored by **Relevance**). The after-rerank
  block also shows a plain-language match band and **how far each chunk moved** — so you can see
  at a glance that reranking genuinely reordered the list rather than just trimming the tail.

```
------------------------------------------------------------------------
WITH RERANKING — Top-K candidates BEFORE rerank
Score = Similarity: how close the chunk is to the query in meaning
Query: What should I do if I fall sick while working from home?  |  5 chunk(s)
------------------------------------------------------------------------
[1] chunkIndex=3  Similarity 46.0%
Every confirmed employee is entitled to 12 sick leaves per calendar year ...
------------------------------------------------------------------------
[2] chunkIndex=2  Similarity 42.1%
Sick leave during probation will be recorded and considered ...
------------------------------------------------------------------------
[3] chunkIndex=4  Similarity 39.4%
Sick leave cannot be combined with casual leave to extend a holiday ...
------------------------------------------------------------------------
[4] chunkIndex=6  Similarity 33.8%
If you are working from home and fall sick during the day, you must not continue working ...
------------------------------------------------------------------------
...

------------------------------------------------------------------------
WITH RERANKING — Top-N AFTER rerank (why these 3 were chosen)
Score = Relevance: how well the chunk actually answers the query
Query: What should I do if I fall sick while working from home?
------------------------------------------------------------------------
#1  Relevance 97.2% (Excellent match)  ↑ up from #4 by similarity
chunkIndex=6
If you are working from home and fall sick during the day, you must not continue working ...
------------------------------------------------------------------------
#2  Relevance 68.0% (Strong match)  ↑ up from #1 by similarity
chunkIndex=3
Every confirmed employee is entitled to 12 sick leaves per calendar year ...
------------------------------------------------------------------------
#3  Relevance 41.3% (Moderate match)  ↓ down from #2 by similarity
chunkIndex=2
Sick leave during probation will be recorded and considered ...
------------------------------------------------------------------------
```

The `↑ up from #4 by similarity` line is the key takeaway: the chunk that plain search ranked
4th — too low to ever reach the LLM — is exactly the one the reranker judged most relevant and
promoted to 1st.

### More queries to compare

The clearest contrasts come from questions whose real answer is a sentence *buried* inside a
chunk about something else, so plain similarity under-ranks it. Try these against both endpoints
and diff the `chunksUsed`:

```bash
# Answer is spread across three sections (probation, sick leave, notice period)
curl "http://localhost:8080/spring-ai/api/search/without-reranking?query=When%20do%20I%20need%20to%20submit%20a%20medical%20certificate%3F"
curl "http://localhost:8080/spring-ai/api/search/with-reranking?query=When%20do%20I%20need%20to%20submit%20a%20medical%20certificate%3F"

# Answer is spread across getting, caring for, and returning the laptop
curl "http://localhost:8080/spring-ai/api/search/without-reranking?query=What%20are%20my%20responsibilities%20for%20the%20company%20laptop%3F"
curl "http://localhost:8080/spring-ai/api/search/with-reranking?query=What%20are%20my%20responsibilities%20for%20the%20company%20laptop%3F"
```

(Scores and chunk indexes in the samples above are illustrative — your exact values depend on
your index and the handbook content.)
