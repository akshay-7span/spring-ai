package dev.spring.ai.service.impl;

import dev.spring.ai.service.MeetingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MeetingServiceImpl implements MeetingService
{
	/**
	 * ChatClient is used to interact with an AI chat service, sending prompts and receiving responses.
	 */
	private final ChatClient chatClient;

	/**
	     * VectorStore is used to retrieve relevant meeting transcript documents
	     * based on semantic similarity to the user's question. It is declared
	     * final because the service depends on a single store instance.
	     */
	    private final VectorStore vectorStore;

	    /**
	     * Construct the MeetingService implementation.
	     *
	     * @param chatClient  a builder for the {@link ChatClient} used to send prompts and receive responses
	     * @param vectorStore the {@link VectorStore} used to perform semantic similarity searches over meeting transcripts
	     * @throws IOException if building the chat client fails (propagated from the builder)
	     */
	    public MeetingServiceImpl(ChatClient.Builder chatClient, VectorStore vectorStore) throws IOException
	    {
	        // Build the ChatClient once during construction and keep a reference for reuse.
	        this.chatClient = chatClient.build();
	        this.vectorStore = vectorStore;
	    }


	    /**
	     * Answer a user's question using only content retrieved from the meeting transcript vector store.
	     *
	     * Behavior:
	     * - Performs a similarity search against the {@link VectorStore} using the provided question.
	     * - Collects the top matching documents and inserts their text into a prompt template.
	     * - Sends the populated prompt to the {@link ChatClient} and returns the textual result.
	     *
	     * Important: The assistant is instructed to ONLY use information found in the provided context.
	     * If the context does not contain an answer, the assistant should explicitly respond with:
	     * "The meeting transcript does not contain information related to this question."
	     *
	     * @param question the user's natural-language question about meeting content
	     * @return the assistant's answer as plain text
	     */
	    @Override
	    public String askQuestion(String question)
	    {
	        // Prompt template that instructs the assistant to use only the provided context.
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

	        // Query the vector store to fetch the most relevant meeting transcript documents.
	        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(2).build());

	        // Convert the retrieved documents into a list of plain text strings.
	        List<String> contentList = documents.stream().map(Document::getText).toList();

	        // Create a prompt template and populate variables for interpolation.
	        PromptTemplate promptTemplate = new PromptTemplate(promptStr);
	        Map<String, Object> variables = new HashMap<>();
	        variables.put("context", String.join("\n", contentList)); // join documents into a single context block
	        variables.put("question", question);

	        // Create the final prompt that will be sent to the chat client.
	        Prompt prompt = promptTemplate.create(variables);

	        // Send the prompt to the chat client and return the resulting text output.
	        return chatClient.prompt(prompt).call().chatClientResponse().chatResponse().getResult().getOutput().getText();
	    }
}
