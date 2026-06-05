package dev.spring.ai.controller;

import dev.spring.ai.service.PineconeRagMeetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pinecone-rag-meeting")
public class PineconeRagMeetingController
{
	private final PineconeRagMeetingService pineconeRagMeetingService;

	public PineconeRagMeetingController(PineconeRagMeetingService pineconeRagMeetingService)
	{
		this.pineconeRagMeetingService = pineconeRagMeetingService;
	}

	@GetMapping
	public String askQuestion(@RequestParam(name = "question") String question)
	{
		return pineconeRagMeetingService.askQuestion(question);
	}
}