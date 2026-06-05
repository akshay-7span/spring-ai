package dev.spring.ai.service;

import dev.spring.ai.dto.JavaVersion;

import java.util.List;
import java.util.Map;

public interface OutputParser
{
	List<String> listOutputConverter(String prompt);

	Map<String, Object> mapOutputConverter(String prompt);

	JavaVersion beanOutputConverter(String prompt);
}
