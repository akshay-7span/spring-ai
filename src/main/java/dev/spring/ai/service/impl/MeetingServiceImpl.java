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
	 * Using ChatModel directly (instead of ChatClient) gives us full manual control
	 * over the tool-calling round-trip so we can log each step explicitly:
	 *   Step 3 → first LLM call
	 *   Step 4 → LLM's first response (contains tool call requests)
	 *   Step 5 → we execute the tools and collect results
	 *   Step 6 → second LLM call with tool results attached
	 *   Step 7 → final LLM response
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
				- For resource availability questions, call the tools and combine the results
				  with the meeting context to give a complete, specific recommendation.
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

		// ── STEP 3 : First LLM call ──────────────────────────────────────────────
		//
		// ToolCallback[] — what it is:
		//   MethodToolCallbackProvider scans `employeeTools` for every @Tool-annotated
		//   method and creates one ToolCallback per method.  Each callback holds:
		//     (a) the tool definition  — name, description, and JSON parameter schema.
		//         Spring AI serialises this into the `tools[]` array of the OpenAI API
		//         request so the LLM knows which tools are available and what they accept.
		//     (b) the Java method reference — used in STEP 5 to actually execute the tool
		//         when the LLM requests it.
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
		//       The raw first LLM response (containing tool-call requests) is returned
		//       directly to us so we can log it and execute the tools ourselves in STEP 5.
		//
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.toolCallbacks(toolCallbacks)
				.internalToolExecutionEnabled(false)
				.build();

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage(populatedPrompt));

		log.info(SEPARATOR);
		log.info("STEP 3 : Sending first call to LLM");
		log.info("         Tools registered and available to LLM:");
		Arrays.stream(toolCallbacks).forEach(tc ->
				log.info("           - {}  |  {}", tc.getToolDefinition().name(), tc.getToolDefinition().description().trim())
		);
		log.info(SEPARATOR);

		ChatResponse firstResponse = chatModel.call(new Prompt(messages, options));

		// AssistantMessage — what it is:
		//   The OpenAI chat API is stateless; every call must carry the full conversation
		//   history as an ordered list of role-tagged messages (user / assistant / tool).
		//   When the LLM decides to call a tool it returns an `assistant`-role message
		//   whose content is tool-call requests (not plain text).
		//   We capture that message here so we can push it back into the history in STEP 5
		//   before adding tool results.  Without it, OpenAI would receive `tool`-role
		//   messages with no prior `assistant` message that requested them, causing an
		//   API error or a misinterpreted context.
		AssistantMessage assistantMessage = firstResponse.getResult().getOutput();

		// ── STEP 4 : Log first LLM response ─────────────────────────────────────
		log.info(SEPARATOR);
		log.info("STEP 4 : First LLM response received");
		log.info("         Finish reason : {}", firstResponse.getResult().getMetadata().getFinishReason());

		List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

		if (toolCalls == null || toolCalls.isEmpty())
		{
			log.info("         LLM answered directly from context — no tool calls were made.");
			log.info(SEPARATOR);
			return assistantMessage.getText();
		}

		log.info("         LLM did NOT answer yet — it requested {} tool call(s):", toolCalls.size());
		toolCalls.forEach(tc -> {
			log.info(DIVIDER);
			log.info("           Tool name  : {}", tc.name());
			log.info("           Arguments  : {}", tc.arguments());
		});
		log.info(SEPARATOR);

		// ── STEP 5 : Execute tools and collect results ───────────────────────────
		// Push the LLM's assistant message (containing tool-call requests) into the history.
		// OpenAI requires this to appear before the tool-result messages sent in STEP 6.
		messages.add(assistantMessage);

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		log.info(SEPARATOR);
		log.info("STEP 5 : Executing tool(s) requested by LLM");

		for (AssistantMessage.ToolCall toolCall : toolCalls)
		{
			log.info(DIVIDER);
			log.info("         Executing tool : {}", toolCall.name());

			ToolCallback matchedCallback = Arrays.stream(toolCallbacks)
					.filter(cb -> cb.getToolDefinition().name().equals(toolCall.name()))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("No registered callback for tool: " + toolCall.name()));

			String toolResult = matchedCallback.call(toolCall.arguments());

			log.info("         Tool result :");
			log.info("{}", toolResult);

			toolResponses.add(new ToolResponseMessage.ToolResponse(
					toolCall.id(), toolCall.name(), toolResult
			));
		}
		log.info(SEPARATOR);

		// ── STEP 6 : Second LLM call with tool results ───────────────────────────
		messages.add(new ToolResponseMessage(toolResponses, Map.of()));

		log.info(SEPARATOR);
		log.info("STEP 6 : Sending second call to LLM with tool results attached");
		log.info("         Tool results being sent back:");
		toolResponses.forEach(tr -> log.info("           - {} : {} chars of data", tr.name(), tr.responseData().length()));
		log.info(SEPARATOR);

		ChatResponse finalResponse = chatModel.call(new Prompt(messages, options));

		// ── STEP 7 : Final response ───────────────────────────────────────────────
		log.info(SEPARATOR);
		log.info("STEP 7 : Final LLM response received (LLM combined context + tool results)");
		log.info(SEPARATOR);

		return finalResponse.getResult().getOutput().getText();
	}
}