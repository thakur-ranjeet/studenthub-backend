package com.example.demo;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ChatbotServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotServiceApplication.class, args);
	}

	// For the other StudentHub services: fail fast, so a stuck service can never hang the chat.
	@Bean("serviceRest")
	RestTemplate serviceRest(RestTemplateBuilder builder) {
		return builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(8)).build();
	}

	// For the AI provider: drafting a whole course page can take a while.
	@Bean("llmRest")
	RestTemplate llmRest(RestTemplateBuilder builder, @Value("${ai.read-timeout-seconds:120}") int seconds) {
		return builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(seconds)).build();
	}
}
