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
	/** Service handling meeting-related operations. */
	private final MeetingService promptService;

	/**
	 * Constructs a MeetingController with the required MeetingService.
	 *
	 * @param promptService the service used to handle meeting questions; injected by Spring
	 */
	public MeetingController(MeetingService promptService)
	{
	    // assign injected service to the controller field
	    this.promptService = promptService;
	}


	/**
	 * Handles HTTP GET requests to ask a question about a meeting.
	 *
	 * Example: GET /meeting?meetingId=123&question=What+was+decided
	 *
	 * @param meetingId the identifier of the meeting to query
	 * @param question the question to ask about the meeting
	 * @return the answer produced by the MeetingService for the provided meeting and question
	 */
	@GetMapping
	public String askQuestion(@RequestParam("meetingId") String meetingId, @RequestParam("question") String question)
	{
	    // delegate to the service which contains the business logic
	    return promptService.askQuestion(meetingId, question);
	}
}
