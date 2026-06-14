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

	// Safety guard — prevents runaway loops if the LLM keeps requesting tools unexpectedly.
	private static final int MAX_ITERATIONS = 10;

	// ChatModel is used directly instead of ChatClient so we can control each iteration
	// manually and log every step. ChatClient would hide the intermediate tool-call
	// round-trips behind a single blocking call.
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
			log.info("         Chunk {} : {}", i + 1, contentList.get(i));
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
		log.info("STEP 2 : Prompt constructed with {} RAG chunk(s) injected", contentList.size());
		log.info(SEPARATOR);

		// ── STEP 3 : Register tools and seed conversation history ────────────────
		//
		// MethodToolCallbackProvider scans EmployeeTools for @Tool methods and creates
		// one ToolCallback per method — each holding the tool definition (name, description,
		// JSON schema) sent to the LLM, and the Java method reference used to execute it.
		//
		// internalToolExecutionEnabled(false) disables Spring AI's silent auto-execution.
		// Without this, Spring AI would intercept tool-call responses, run them internally,
		// and return only the final answer — we would never see the intermediate iterations.
		//
		ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
				.toolObjects(employeeTools)
				.build()
				.getToolCallbacks();

		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.toolCallbacks(toolCallbacks)
				.internalToolExecutionEnabled(false)
				.build();

		// OpenAI's API is stateless — the full conversation history must be re-sent every call.
		// We seed with the user message and grow the list inside the loop:
		//   user → assistant (tool requests) → tool results → assistant → ...
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage(populatedPrompt));

		log.info(SEPARATOR);
		log.info("STEP 3 : Tools registered — starting agentic loop (max {} iterations)", MAX_ITERATIONS);
		log.info("         Tools available to LLM:");
		Arrays.stream(toolCallbacks).forEach(tc ->
				log.info("           - {} | {}", tc.getToolDefinition().name(), tc.getToolDefinition().description().trim())
		);
		log.info(SEPARATOR);

		// ── AGENTIC LOOP ─────────────────────────────────────────────────────────
		//
		// Each iteration:
		//   1. Call the LLM with the full conversation history.
		//   2. Append the AssistantMessage to history (required by OpenAI before tool results).
		//   3. If no tool calls in the response → return the final answer.
		//   4. If tool calls present → execute each, append ToolResponseMessage, loop again.
		//
		for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++)
		{
			log.info(SEPARATOR);
			log.info("AGENTIC LOOP — Iteration {} / {} | Sending {} message(s) to LLM", iteration, MAX_ITERATIONS, messages.size());
			log.info(SEPARATOR);

			ChatResponse response = chatModel.call(new Prompt(messages, options));
			AssistantMessage assistantMessage = response.getResult().getOutput();
			messages.add(assistantMessage);

			List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
			log.info("AGENTIC LOOP — Iteration {} | Finish reason: {} | Tool calls: {}",
					iteration, response.getResult().getMetadata().getFinishReason(), toolCalls.size());

			if (toolCalls.isEmpty())
			{
				log.info("               No tool calls — LLM has enough data to answer.");
				log.info(SEPARATOR);
				log.info("AGENTIC LOOP COMPLETE — Final answer after {} iteration(s):", iteration);
				log.info(DIVIDER);
				log.info("{}", assistantMessage.getText());
				log.info(SEPARATOR);
				return assistantMessage.getText();
			}

			toolCalls.forEach(tc -> log.info("               → Tool: {} | Args: {}", tc.name(), tc.arguments()));

			// Execute each requested tool and collect results
			List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

			for (AssistantMessage.ToolCall toolCall : toolCalls)
			{
				ToolCallback matchedCallback = Arrays.stream(toolCallbacks)
						.filter(cb -> cb.getToolDefinition().name().equals(toolCall.name()))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("No registered callback for tool: " + toolCall.name()));

				String toolResult = matchedCallback.call(toolCall.arguments());

				toolResponses.add(new ToolResponseMessage.ToolResponse(
						toolCall.id(), toolCall.name(), toolResult
				));
			}

			messages.add(new ToolResponseMessage(toolResponses, Map.of()));

			log.info("               All tools executed — feeding results back to LLM → Iteration {}", iteration + 1);
			log.info(SEPARATOR);
		}

		log.warn(SEPARATOR);
		log.warn("AGENTIC LOOP STOPPED — Reached max iterations ({}) without a final answer.", MAX_ITERATIONS);
		log.warn(SEPARATOR);
		return "Unable to produce a final answer within the allowed number of reasoning steps.";
	}
}
