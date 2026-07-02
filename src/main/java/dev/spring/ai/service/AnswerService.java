package dev.spring.ai.service;

import dev.spring.ai.model.ChunkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds a grounded RAG prompt from a set of chunks and asks the LLM to answer
 * using only that context. Both search endpoints share this service so the only
 * thing that differs between them is <em>which</em> chunks are supplied — which
 * is exactly what the demo highlights.
 */
@Service
public class AnswerService
{
	private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

	/** OpenAI chat model used to produce the final grounded answer. */
	private final ChatModel chatModel;

	/**
	 * @param chatModel the auto-configured OpenAI chat model
	 */
	public AnswerService(ChatModel chatModel)
	{
		this.chatModel = chatModel;
	}

	/**
	 * Generates an answer to {@code query} grounded only in the supplied chunks.
	 *
	 * @param query  the user's question
	 * @param chunks the context chunks the model is allowed to use
	 * @return the model's answer, or a "not found" message if the context lacks it
	 */
	public String generateAnswer(String query, List<ChunkResult> chunks)
	{
		// Number each chunk so the prompt is readable and the model can cite context.
		StringBuilder context = new StringBuilder();
		for (int i = 0; i < chunks.size(); i++)
		{
			context.append("[Chunk ").append(i + 1).append("]:\n")
					.append(chunks.get(i).content())
					.append("\n\n");
		}

		// Strictly grounded prompt: answer only from context, otherwise say so.
		String promptText = """
				You are a helpful assistant. Answer the user's question based only on
				the context provided below. Do not use any outside knowledge.
				If the answer is not found in the context, say 'I could not find the
				answer in the provided documents.'

				Context:
				%s
				Question: %s

				Answer:""".formatted(context, query);

		// Single blocking call to the chat model; getText() per Spring AI 1.0.8.
		log.info("Calling LLM with {} chunks of context", chunks.size());
		return chatModel.call(new Prompt(promptText))
				.getResult()
				.getOutput()
				.getText();
	}
}
