package dev.spring.ai.controller;

import dev.spring.ai.service.MeetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meeting")
public class MeetingController
{
	private final MeetingService meetingService;

	public MeetingController(MeetingService meetingService)
	{
		this.meetingService = meetingService;
	}

	/**
	 * Ask any question about the SolarVision project.
	 *
	 * The service handles two types of questions seamlessly:
	 *  - Meeting content  (e.g. "What is the go-live date?")
	 *    → answered from the meeting transcript retrieved via RAG
	 *  - Resource availability (e.g. "Who is available for the Phase 2 work in April?")
	 *    → RAG retrieves the requirement from the meeting; tool calling fetches
	 *      live leave records and project allocations; LLM combines both
	 *
	 * Example requests:
	 *   GET /meeting?question=What is the go-live date?
	 *   GET /meeting?question=Who is available for the Phase 2 backend work in April 2026?
	 *
	 * @param question the user's natural-language question
	 * @return the LLM's answer as plain text
	 */
	@GetMapping
	public String askQuestion(@RequestParam(name = "question", required = true) String question)
	{
		return meetingService.askQuestion(question);
	}
}