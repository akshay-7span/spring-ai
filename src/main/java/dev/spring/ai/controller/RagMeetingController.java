package dev.spring.ai.controller;

import dev.spring.ai.service.RagMeetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag-meeting")
public class RagMeetingController
{
	private final RagMeetingService ragMeetingService;

	public RagMeetingController(RagMeetingService ragMeetingService)
	{
		this.ragMeetingService = ragMeetingService;
	}

	@GetMapping
	public String askQuestion(@RequestParam(name = "question", required = true) String question)
	{
		return ragMeetingService.askQuestion(question);
	}
}