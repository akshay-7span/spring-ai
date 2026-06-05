package dev.spring.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig
{
	@Bean
	public RestTemplate restTemplate()
	{
		return new RestTemplate();
	}

	// JdkClientHttpRequestFactory is required for the tool-calling two-round-trip flow.
	// The default SimpleClientHttpRequestFactory fails when HttpURLConnection tries to
	// retry after Spring AI feeds the tool result back to the LLM in a second request.
	@Bean
	public RestClient.Builder restClientBuilder()
	{
		return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
	}
}