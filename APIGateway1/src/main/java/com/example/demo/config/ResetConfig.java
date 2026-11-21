package com.example.demo.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResetConfig {

	// Two background threads for e-mail work, so a slow mail server never slows (or reveals anything about) a request.
	@Bean(name = "resetExecutor", destroyMethod = "shutdown")
	public ExecutorService resetExecutor() {
		return Executors.newFixedThreadPool(2);
	}
}
