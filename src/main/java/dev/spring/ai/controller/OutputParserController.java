package dev.spring.ai.controller;

import dev.spring.ai.dto.JavaVersion;
import dev.spring.ai.service.OutputParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/output-parser")
public class OutputParserController
{
	private final OutputParser outputParser;

	public OutputParserController(OutputParser outputParser)
	{
		this.outputParser = outputParser;
	}

	@GetMapping("/list")
	public List<String> parseOutput(@RequestParam String prompt)
	{
		return outputParser.listOutputConverter(prompt);
	}

	@GetMapping("/map")
	public Map<String, Object> parseMapOutput(@RequestParam String prompt)
	{
		return outputParser.mapOutputConverter(prompt);
	}

	@GetMapping("/bean")
	public JavaVersion parseBeanOutput(@RequestParam String prompt)
	{
		return outputParser.beanOutputConverter(prompt);
	}
}