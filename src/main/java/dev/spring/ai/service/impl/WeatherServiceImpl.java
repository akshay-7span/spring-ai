package dev.spring.ai.service.impl;

import dev.spring.ai.service.WeatherService;
import dev.spring.ai.tools.WeatherTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WeatherServiceImpl implements WeatherService
{
	private static final Logger log = LoggerFactory.getLogger(WeatherServiceImpl.class);

	private final ChatClient chatClient;
	private final WeatherTools weatherTools;

	public WeatherServiceImpl(ChatClient.Builder builder, WeatherTools weatherTools)
	{
		this.chatClient = builder.build();
		this.weatherTools = weatherTools;
	}

	private static final String SYSTEM_PROMPT = """
			You are a helpful weather assistant.
			When the user asks about the weather in any city, call the getCurrentWeather tool with that city name.
			Once the tool returns the live weather data, use it to answer the user's question in a clear and friendly way.
			Do not guess or use your training data for current weather — always use the tool result.
			""";

	@Override
	public String askWeather(String question)
	{
		log.info("================================================================");
		log.info("STEP 1 : Sending question to LLM → '{}'", question);
		log.info("         LLM will detect it needs live data and call the tool.");
		log.info("================================================================");

		// Spring AI sends the question + tool definition to the LLM.
		// The LLM responds with a tool-call request (not a final answer yet).
		// Spring AI invokes WeatherTools.getCurrentWeather(), feeds the result
		// back to the LLM, and returns the final answer — all automatically.
		String answer = chatClient.prompt()
				.system(SYSTEM_PROMPT)
				.user(question)
				.tools(weatherTools)
				.call()
				.content();

		log.info("================================================================");
		log.info("STEP 3 : Final answer received → '{}'", answer);
		log.info("================================================================");

		return answer;
	}
}