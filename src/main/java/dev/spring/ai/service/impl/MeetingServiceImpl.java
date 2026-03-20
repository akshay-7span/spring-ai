package dev.spring.ai.service.impl;

import dev.spring.ai.service.MeetingService;
import dev.spring.ai.tools.EmployeeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class MeetingServiceImpl implements MeetingService
{
	private static final Logger log = LoggerFactory.getLogger(MeetingServiceImpl.class);
	private static final String SEPARATOR = "=".repeat(72);
	private static final String DIVIDER   = "-".repeat(72);

	/**
	 * Maximum number of agentic loop iterations allowed per request.
	 *
	 * Each iteration is one LLM call.  The loop exits naturally when the LLM
	 * produces a plain-text answer (no more tool calls).  This cap is a safety
	 * guard against runaway loops caused by unexpected LLM behaviour.
	 */
	private static final int MAX_ITERATIONS = 10;

	/**
	 * Using ChatModel directly (instead of ChatClient) gives us full manual control
	 * over the agentic loop so we can log each iteration explicitly.
	 *
	 * Agentic loop — how it works:
	 *   Each iteration = one LLM call.
	 *   - If the LLM response contains tool-call requests:
	 *       execute the requested tools, append results to conversation history,
	 *       and start the next iteration.
	 *   - If the LLM response is a plain-text answer:
	 *       return it — the loop is done.
	 *
	 * Why this enables multi-step reasoning:
	 *   Iteration 1 — LLM calls getTeamProjectAllocations → learns who is free
	 *   Iteration 2 — LLM calls getEmployeeSkillProfile for each free person
	 *                  → learns their technical skills
	 *   Iteration 3 — LLM calls getTeamLeaveRecords to confirm no leave conflicts
	 *   Final        — LLM has enough data, produces a recommendation
	 *
	 * The LLM cannot know which employees to profile until it has seen the
	 * allocation result.  That sequential dependency is what forces the loop —
	 * it cannot be collapsed into a single batch of tool calls.
	 */
	private final ChatModel chatModel;
	private final VectorStore vectorStore;
	private final EmployeeTools employeeTools;

	public MeetingServiceImpl(ChatModel chatModel, VectorStore vectorStore, EmployeeTools employeeTools)
	{
		this.chatModel = chatModel;
		this.vectorStore = vectorStore;
		this.employeeTools = employeeTools;
	}

	@Override
	public String askQuestion(String question)
	{
		// ── STEP 1 : RAG ─────────────────────────────────────────────────────────
		log.info(SEPARATOR);
		log.info("STEP 1 : Retrieving relevant context from Pinecone (RAG)");
		log.info("         Question : {}", question);
		log.info(SEPARATOR);

		List<Document> documents = vectorStore.similaritySearch(
				SearchRequest.builder().query(question).topK(3).build()
		);
		log.info("         {} chunk(s) retrieved from Pinecone", documents.size());

		List<String> contentList = documents.stream().map(Document::getText).toList();
		for (int i = 0; i < contentList.size(); i++)
		{
			log.info(DIVIDER);
			log.info("         Chunk {} :", i + 1);
			log.info("{}", contentList.get(i));
		}
		log.info(SEPARATOR);

		// ── STEP 2 : Build prompt ────────────────────────────────────────────────
		String promptStr = """
				You are an intelligent assistant for the SolarVision project.

				You have two sources of information to answer questions:

				1. The project kickoff meeting transcript is provided below as context.
				   Use it to answer questions about project goals, timeline, technology decisions,
				   resource requirements, and client discussions.

				2. You have access to tools that fetch live employee data from HR and
				   resource management systems. Use them when the question involves
				   resource availability, who is free, or team capacity planning.

				When answering:
				- For meeting content questions, use the context below.
				- For resource availability questions, use the available tools to gather
				  the data you need, then provide a complete recommendation.
				- If neither the context nor the tools contain enough information, respond:
				  "The available information does not contain enough detail to answer this question."

				Context (retrieved from project kickoff meeting):
				{context}

				Question:
				{question}
				""";

		PromptTemplate promptTemplate = new PromptTemplate(promptStr);
		Map<String, Object> variables = new HashMap<>();
		variables.put("context", String.join("\n", contentList));
		variables.put("question", question);
		String populatedPrompt = promptTemplate.create(variables).getContents();

		log.info(SEPARATOR);
		log.info("STEP 2 : Final prompt constructed");
		log.info(DIVIDER);
		log.info("{}", populatedPrompt);
		log.info(SEPARATOR);

		// ── STEP 3 : Register tools and seed conversation history ────────────────
		//
		// ToolCallback[] — what it is:
		//   MethodToolCallbackProvider scans `employeeTools` for every @Tool-annotated
		//   method and creates one ToolCallback per method.  Each callback holds:
		//     (a) the tool definition  — name, description, and JSON parameter schema.
		//         Spring AI serialises this into the `tools[]` array of the OpenAI API
		//         request so the LLM knows which tools are available and what they accept.
		//     (b) the Java method reference — used inside the agentic loop to actually
		//         execute the tool when the LLM requests it.
		//
		ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
				.toolObjects(employeeTools)
				.build()
				.getToolCallbacks();

		// OpenAiChatOptions — what it is:
		//   Per-request configuration attached to a Prompt.  Unlike application-level
		//   settings in application.properties, this is built fresh per call.
		//
		//   .toolCallbacks(toolCallbacks)
		//       Populates the `tools[]` field of the OpenAI API request, telling the LLM
		//       which tools it may call.
		//
		//   .internalToolExecutionEnabled(false)
		//       By default, Spring AI intercepts the LLM's tool-call requests, executes
		//       them silently, and returns only the final answer — we never see the
		//       intermediate steps.  Setting this to false disables that auto-execution.
		//       Each raw LLM response is returned directly to us so we can log it,
		//       execute the tools, and drive the agentic loop ourselves.
		//
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.toolCallbacks(toolCallbacks)
				.internalToolExecutionEnabled(false)
				.build();

		// The message list is the full conversation history sent to OpenAI on every call.
		// OpenAI's API is stateless — the complete history must be re-sent each time.
		// We seed it with the user's prompt and grow it inside the agentic loop:
		//   user → assistant (tool requests) → tool results → assistant (tool requests) → ...
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage(populatedPrompt));

		log.info(SEPARATOR);
		log.info("STEP 3 : Tools registered — starting agentic loop (max {} iterations)", MAX_ITERATIONS);
		log.info("         Tools available to LLM:");
		Arrays.stream(toolCallbacks).forEach(tc ->
				log.info("           - {}  |  {}", tc.getToolDefinition().name(), tc.getToolDefinition().description().trim())
		);
		log.info(SEPARATOR);

		// ── AGENTIC LOOP ─────────────────────────────────────────────────────────
		//
		// Each iteration:
		//   1. Call the LLM with the current conversation history.
		//   2. Capture the AssistantMessage from the response and add it to history.
		//      AssistantMessage — what it is:
		//        The LLM's response wrapped in an `assistant`-role message.
		//        It is either a plain-text final answer (no tool calls) or a set of
		//        tool-call requests (no text yet).  It must always be appended to the
		//        history so OpenAI sees the full conversation on the next call.
		//   3. If the response has no tool calls → return the answer (loop ends).
		//   4. If the response has tool calls:
		//        a. Execute each requested tool.
		//        b. Wrap results in a ToolResponseMessage and append to history.
		//           ToolResponseMessage carries `tool`-role messages.  OpenAI requires
		//           a preceding `assistant` message that requested them — that is why
		//           the AssistantMessage is always added to history before this step.
		//        c. Continue to the next iteration — LLM will reason over the results.
		//
		for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++)
		{
			// ── LLM CALL ─────────────────────────────────────────────────────────
			log.info(SEPARATOR);
			log.info("AGENTIC LOOP — Iteration {} / {}  |  Sending request to LLM", iteration, MAX_ITERATIONS);
			log.info("               Conversation history : {} message(s) in context", messages.size());
			log.info(SEPARATOR);

			ChatResponse response = chatModel.call(new Prompt(messages, options));
			AssistantMessage assistantMessage = response.getResult().getOutput();

			// Always append the assistant message before checking for tool calls.
			// OpenAI requires the assistant turn to precede any tool-result turns.
			messages.add(assistantMessage);

			// ── LLM RESPONSE ─────────────────────────────────────────────────────
			log.info(SEPARATOR);
			log.info("AGENTIC LOOP — Iteration {}  |  LLM response received", iteration);
			log.info("               Finish reason : {}", response.getResult().getMetadata().getFinishReason());

			List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

			if (toolCalls.isEmpty())
			{
				// finish_reason = "stop" — the LLM produced its final answer.
				// No more tool calls requested.  The agentic loop ends here.
				log.info("               No tool calls requested — LLM has enough data to answer.");
				log.info(SEPARATOR);
				log.info("AGENTIC LOOP — COMPLETE  |  Final answer produced after {} iteration(s)", iteration);
				log.info(DIVIDER);
				log.info("FINAL ANSWER :");
				log.info(DIVIDER);
				log.info("{}", assistantMessage.getText());
				log.info(SEPARATOR);
				return assistantMessage.getText();
			}

			// finish_reason = "tool_calls" — the LLM needs more data before answering.
			log.info("               LLM requested {} tool call(s) — will execute and loop back", toolCalls.size());
			toolCalls.forEach(tc -> {
				log.info(DIVIDER);
				log.info("                 Tool      : {}", tc.name());
				log.info("                 Arguments : {}", tc.arguments());
			});
			log.info(SEPARATOR);

			// ── TOOL EXECUTION ───────────────────────────────────────────────────
			log.info(SEPARATOR);
			log.info("AGENTIC LOOP — Iteration {}  |  Executing {} tool(s)", iteration, toolCalls.size());

			List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

			for (AssistantMessage.ToolCall toolCall : toolCalls)
			{
				log.info(DIVIDER);
				log.info("               Tool       : {}", toolCall.name());
				log.info("               Arguments  : {}", toolCall.arguments());

				ToolCallback matchedCallback = Arrays.stream(toolCallbacks)
						.filter(cb -> cb.getToolDefinition().name().equals(toolCall.name()))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("No registered callback for tool: " + toolCall.name()));

				String toolResult = matchedCallback.call(toolCall.arguments());

				log.info("               Result     : {}", toolResult);

				toolResponses.add(new ToolResponseMessage.ToolResponse(
						toolCall.id(), toolCall.name(), toolResult
				));
			}

			// Append all tool results as a single ToolResponseMessage.
			// The next iteration will send this enriched history back to the LLM,
			// which will reason over the new data and decide whether to call more
			// tools or produce its final answer.
			messages.add(new ToolResponseMessage(toolResponses, Map.of()));

			log.info(DIVIDER);
			log.info("AGENTIC LOOP — Iteration {}  |  All tools executed — feeding results back to LLM", iteration);
			log.info("               Results summary:");
			toolResponses.forEach(tr -> log.info("                 - {} → {} chars", tr.name(), tr.responseData().length()));
			log.info("               → Proceeding to Iteration {}", iteration + 1);
			log.info(SEPARATOR);
		}

		// ── SAFETY EXIT ───────────────────────────────────────────────────────────
		// Reached only if MAX_ITERATIONS completed without the LLM producing a final
		// answer.  Should not happen under normal operation.
		log.warn(SEPARATOR);
		log.warn("AGENTIC LOOP — STOPPED  |  Reached max iterations ({}) without a final answer.", MAX_ITERATIONS);
		log.warn(SEPARATOR);
		return "Unable to produce a final answer within the allowed number of reasoning steps.";
	}
}