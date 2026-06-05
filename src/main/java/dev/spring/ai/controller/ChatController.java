package dev.spring.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController
{
	private final ChatClient chatClient;

	public ChatController(ChatClient.Builder chatClientBuilder)
	{
		this.chatClient = chatClientBuilder.build();
	}


	/**
	 * Handles GET requests to the /chat endpoint.
	 *
	 * @param message The user's message to send to the chat service.
	 * @return The chat response as a String.
	 *
	 * The chatClient is an instance of ChatClient, which is used to interact with an AI chat service.
	 *
	 * ChatClient main methods used here:
	 * - prompt(): Starts building a chat prompt.
	 * - user(String message): Sets the user's message in the prompt.
	 * - call(): Sends the prompt to the AI service and gets a response.
	 * - chatResponse(): Retrieves the chat response object.
	 * - toString(): Converts the chat response to a String for returning to the client.
	 */
	@GetMapping
	public String generateChat(@RequestParam("message") String message)
	{
	    return chatClient.prompt().user(message).call().chatResponse().toString();
	}
}
