package com.example.demo;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

	// Timeouts so a stuck Course service can never make this service hang forever
	@Bean
	RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(10)).build();
	}
}
