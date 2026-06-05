package dev.spring.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WeatherTools
{
	private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

	private static final Map<Integer, String> WEATHER_CODES = Map.ofEntries(
			Map.entry(0,  "Clear sky"),
			Map.entry(1,  "Mainly clear"),
			Map.entry(2,  "Partly cloudy"),
			Map.entry(3,  "Overcast"),
			Map.entry(45, "Fog"),
			Map.entry(48, "Icy fog"),
			Map.entry(51, "Light drizzle"),
			Map.entry(53, "Moderate drizzle"),
			Map.entry(55, "Dense drizzle"),
			Map.entry(61, "Slight rain"),
			Map.entry(63, "Moderate rain"),
			Map.entry(65, "Heavy rain"),
			Map.entry(71, "Slight snow"),
			Map.entry(73, "Moderate snow"),
			Map.entry(75, "Heavy snow"),
			Map.entry(80, "Slight rain showers"),
			Map.entry(81, "Moderate rain showers"),
			Map.entry(82, "Violent rain showers"),
			Map.entry(95, "Thunderstorm"),
			Map.entry(96, "Thunderstorm with slight hail"),
			Map.entry(99, "Thunderstorm with heavy hail")
	);

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	public WeatherTools(RestTemplate restTemplate, ObjectMapper objectMapper)
	{
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
	}

	@Tool(description = """
			Fetches current weather conditions for a given city name.
			Returns temperature (Celsius), feels-like temperature, weather description,
			humidity, and wind speed. Call this whenever the user asks about the weather in any city.
			""")
	public String getCurrentWeather(String city)
	{
		log.info("----------------------------------------------------------------");
		log.info("TOOL CALL : LLM requested → getCurrentWeather(\"{}\")", city);

		try
		{
			// Step 1: Geocoding — resolve city name to lat/lon
			String geocodeUrl = UriComponentsBuilder
					.fromHttpUrl("https://geocoding-api.open-meteo.com/v1/search")
					.queryParam("name", city)
					.queryParam("count", 1)
					.toUriString();

			String geocodeResponse = restTemplate.getForObject(geocodeUrl, String.class);
			JsonNode location = objectMapper.readTree(geocodeResponse).path("results").get(0);

			if (location == null)
			{
				return "City \"" + city + "\" not found. Please check the city name and try again.";
			}

			String resolvedCity = location.path("name").asText();
			String country = location.path("country").asText();
			double latitude = location.path("latitude").asDouble();
			double longitude = location.path("longitude").asDouble();

			log.info("           Geocoded : {} → {}, {} (lat={}, lon={})", city, resolvedCity, country, latitude, longitude);

			// Step 2: Weather — fetch current conditions using resolved coordinates
			String weatherUrl = UriComponentsBuilder
					.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
					.queryParam("latitude", latitude)
					.queryParam("longitude", longitude)
					.queryParam("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code")
					.queryParam("wind_speed_unit", "kmh")
					.toUriString();

			String weatherResponse = restTemplate.getForObject(weatherUrl, String.class);
			JsonNode current = objectMapper.readTree(weatherResponse).path("current");

			double tempC = current.path("temperature_2m").asDouble();
			double feelsLikeC = current.path("apparent_temperature").asDouble();
			int humidity = current.path("relative_humidity_2m").asInt();
			double windSpeed = current.path("wind_speed_10m").asDouble();
			int weatherCode = current.path("weather_code").asInt();
			String description = WEATHER_CODES.getOrDefault(weatherCode, "Unknown");

			String result = String.format(
					"Location: %s, %s | Temperature: %.1f°C (Feels like %.1f°C) | Condition: %s | Humidity: %d%% | Wind: %.1f km/h",
					resolvedCity, country, tempC, feelsLikeC, description, humidity, windSpeed);

			log.info("           Result   : {}", result);
			log.info("----------------------------------------------------------------");
			return result;
		}
		catch (Exception e)
		{
			log.error("Failed to fetch weather for city: {}", city, e);
			return "Unable to fetch weather data for \"" + city + "\". Please check the city name and try again.";
		}
	}
}