package dev.spring.ai.controller;

import dev.spring.ai.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/weather")
public class WeatherController
{
	private final WeatherService weatherService;

	public WeatherController(WeatherService weatherService)
	{
		this.weatherService = weatherService;
	}

	@GetMapping
	public String askWeather(@RequestParam String question)
	{
		return weatherService.askWeather(question);
	}
}