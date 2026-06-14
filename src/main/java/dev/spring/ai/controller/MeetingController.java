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

	@GetMapping
	public String askQuestion(@RequestParam(name = "question") String question)
	{
		return meetingService.askQuestion(question);
	}
}
