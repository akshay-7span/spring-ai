package dev.spring.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class MeetingTools {

    @Tool(name = "get_current_time",
          description = "Returns the current time for a given timezone. " +
                        "Use this when the user asks what time it is in a " +
                        "specific city or country. " +
                        "Example timezones: Asia/Kolkata, America/New_York, Europe/London")
    public String getCurrentTime(String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a, EEEE dd MMMM yyyy");
            return "Current time in " + timezone + " is: " + now.format(formatter);
        } catch (Exception e) {
            return "Invalid timezone: " + timezone +
                   ". Please use a valid timezone like Asia/Kolkata or America/New_York.";
        }
    }
}