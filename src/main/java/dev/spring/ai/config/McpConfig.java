package dev.spring.ai.config;

import dev.spring.ai.tools.MeetingTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider meetingToolsProvider(MeetingTools meetingTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(meetingTools)
                .build();
    }
}