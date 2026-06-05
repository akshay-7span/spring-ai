package dev.spring.ai.service.impl;

import dev.spring.ai.dto.JavaVersion;
import dev.spring.ai.service.OutputParser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OutputParserImpl implements OutputParser
{
	private static final String PROMPT_TEMPLATE = """
			Answer the following question.

			Question: {question}

			{format}
			""";

	private final ChatClient chatClient;

	public OutputParserImpl(ChatClient.Builder chatClient)
	{
		this.chatClient = chatClient.build();
	}

	@Override
	public List<String> listOutputConverter(String prompt)
	{
		ListOutputConverter converter = new ListOutputConverter(new DefaultConversionService());

		String fullPrompt = new PromptTemplate(PROMPT_TEMPLATE)
				.create(Map.of("question", prompt, "format", converter.getFormat()))
				.getContents();

		String response = chatClient.prompt().user(fullPrompt).call().chatResponse().getResult().getOutput().getText();
		System.out.println("ChatGPT Response: " + response);

		return converter.convert(response);
	}

	@Override
	public Map<String, Object> mapOutputConverter(String prompt)
	{
		MapOutputConverter converter = new MapOutputConverter();

		String fullPrompt = new PromptTemplate(PROMPT_TEMPLATE)
				.create(Map.of("question", prompt, "format", converter.getFormat()))
				.getContents();

		String response = chatClient.prompt().user(fullPrompt).call().chatResponse().getResult().getOutput().getText();
		System.out.println("ChatGPT Response: " + response);

		return converter.convert(response);
	}

	@Override
	public JavaVersion beanOutputConverter(String prompt)
	{
		BeanOutputConverter<JavaVersion> converter = new BeanOutputConverter<>(JavaVersion.class);

		String fullPrompt = new PromptTemplate(PROMPT_TEMPLATE)
				.create(Map.of("question", prompt, "format", converter.getFormat()))
				.getContents();

		String response = chatClient.prompt().user(fullPrompt).call().chatResponse().getResult().getOutput().getText();
		System.out.println("ChatGPT Response: " + response);

		return converter.convert(response);
	}
}