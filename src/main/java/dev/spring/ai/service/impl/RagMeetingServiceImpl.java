package dev.spring.ai.service.impl;

import dev.spring.ai.service.RagMeetingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagMeetingServiceImpl implements RagMeetingService
{
	private final ChatClient chatClient;
	private final VectorStore vectorStore;

	public RagMeetingServiceImpl(ChatClient.Builder chatClient, VectorStore vectorStore)
	{
		this.chatClient = chatClient.build();
		this.vectorStore = vectorStore;
	}

	@Override
	public String askQuestion(String question)
	{
		String promptStr = """
				You are an intelligent assistant designed to answer questions about meeting discussions.

				Your goal is to provide accurate, concise, and context-aware answers using ONLY the information from the provided meeting transcript context.
				If the answer is not available in the context, clearly respond:
				"The meeting transcript does not contain information related to this question."

				Context:
				{context}

				Question:
				{question}
				""";

		List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(2).build());
		List<String> contentList = documents.stream().map(Document::getText).toList();

		PromptTemplate promptTemplate = new PromptTemplate(promptStr);
		Map<String, Object> variables = new HashMap<>();
		variables.put("context", String.join("\n", contentList));
		variables.put("question", question);

		Prompt prompt = promptTemplate.create(variables);

		return chatClient.prompt(prompt).call().chatClientResponse().chatResponse().getResult().getOutput().getText();
	}
}