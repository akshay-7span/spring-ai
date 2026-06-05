package dev.spring.ai.service.impl;

import dev.spring.ai.service.MeetingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class MeetingServiceImpl implements MeetingService
{
	/**
	 * ChatClient is used to interact with an AI chat service, sending prompts and receiving responses.
	 */
	private final ChatClient chatClient;

	/**
	 * Constructs an OutputParserImpl with a ChatClient built from the provided builder.
	 *
	 * @param chatClient the ChatClient.Builder used to build the ChatClient instance
	 */
	public MeetingServiceImpl(ChatClient.Builder chatClient) throws IOException
	{
		this.chatClient = chatClient.build();
		loadTranscripts();
	}

	/**
	 * In-memory cache of meeting transcripts keyed by meeting id.
	 *
	 * Each entry maps a short meeting identifier (for example "meeting1") to the
	 * full transcript text loaded from the resources directory.
	 */
	private final Map<String, String> transcripts = new HashMap<>();

	/**
	 * Loads meeting transcript files from the `src/main/resources/meetings/` folder
	 * into the {@code transcripts} map.
	 *
	 * The method reads the following files:
	 * - `src/main/resources/meetings/meeting1.txt`
	 * - `src/main/resources/meetings/meeting2.txt`
	 * - `src/main/resources/meetings/meeting3.txt`
	 *
	 * @throws IOException if any of the transcript files cannot be read
	 */
	private void loadTranscripts() throws IOException
	{
		// Load each transcript file and associate it with a meeting id.
		transcripts.put("meeting1", Files.readString(Path.of("src/main/resources/meetings/meeting1.txt")));
		transcripts.put("meeting2", Files.readString(Path.of("src/main/resources/meetings/meeting2.txt")));
		transcripts.put("meeting3", Files.readString(Path.of("src/main/resources/meetings/meeting3.txt")));
	}

	/**
	 * Ask a question about a specific meeting transcript.
	 *
	 * This method:
	 * - Retrieves the transcript for the provided {@code meetingId}.
	 * - Constructs a prompt that instructs the assistant to answer ONLY using the transcript.
	 * - Sends the prompt to the configured {@code chatClient} and returns the assistant's text output.
	 *
	 * @param meetingId the identifier of the meeting whose transcript should be used (e.g. "meeting1")
	 * @param question the user question to ask about the transcript
	 * @return the assistant's textual response as returned by the chat client
	 */
	@Override
	public String askQuestion(String meetingId, String question)
	{
		// Compose the prompt by injecting the chosen meeting transcript and the user question.
		String prompt = String.format("""
	           You are an assistant that answers questions based on meeting transcripts.
	
	           Meeting Transcript:
	           %s
	
	           Question:
	           %s
	
	           Answer concisely and base your response ONLY on the transcript.
	           """, transcripts.get(meetingId), question);

		// Send the prompt to the chat client and extract the response content.
		String response = chatClient.prompt().user(prompt).call().chatResponse().getResult().getOutput().getText();
		System.out.println("ChatGPT Response: " + response);

		// Return the raw response text to the caller.
		return response;
	}
}
