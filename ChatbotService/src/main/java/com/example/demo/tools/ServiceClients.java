package com.example.demo.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

// The only place that talks to the other StudentHub services.
@Component
public class ServiceClients {

	private static final Logger log = LoggerFactory.getLogger(ServiceClients.class);

	private final RestTemplate rest;
	private final String courseUrl;
	private final String orderUrl;
	private final String studentUrl;

	public ServiceClients(@Qualifier("serviceRest") RestTemplate rest,
			@Value("${course.service.url}") String courseUrl,
			@Value("${order.service.url}") String orderUrl,
			@Value("${student.service.url}") String studentUrl) {
		this.rest = rest;
		this.courseUrl = courseUrl;
		this.orderUrl = orderUrl;
		this.studentUrl = studentUrl;
	}

	// Course service has no login of its own (the gateway protects it), course data is not private.
	public JsonNode course(String path) {
		return get(courseUrl + path, null, "course");
	}

	// Order service verifies the user's token itself and only answers for that user.
	public JsonNode order(String path, String token) {
		return get(orderUrl + path, token, "order");
	}

	// Student service has no login of its own: callers MUST check the admin role before using this.
	public JsonNode student(String path) {
		return get(studentUrl + path, null, "student");
	}

	private JsonNode get(String url, String token, String service) {
		try {
			HttpHeaders headers = new HttpHeaders();
			if (token != null) {
				headers.setBearerAuth(token);
			}
			JsonNode body = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
			return body;
		} catch (HttpStatusCodeException e) {
			HttpStatusCode code = e.getStatusCode();
			log.warn("{} service answered {} for {}", service, code.value(), url);
			if (code.value() == 404) {
				throw new ToolException("That was not found.");
			}
			if (code.value() == 401 || code.value() == 403) {
				throw new ToolException("You are not allowed to see that.");
			}
			throw new ToolException("The " + service + " service had a problem. Please try again shortly.");
		} catch (RestClientException e) {
			log.warn("{} service not reachable: {}", service, e.getMessage());
			throw new ToolException("The " + service + " service is not reachable right now. Please try again shortly.");
		}
	}
}
