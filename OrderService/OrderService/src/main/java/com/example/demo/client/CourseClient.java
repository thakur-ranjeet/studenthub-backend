package com.example.demo.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.demo.dto.CourseDto;
import com.example.demo.exception.ShopException;

// Reads courses (and their prices) from the Course service. Prices are ALWAYS taken from here,
// never from the browser.
@Component
public class CourseClient {

	private final RestTemplate rest;
	private final String baseUrl;

	public CourseClient(RestTemplate rest, @Value("${course.service.url}") String baseUrl) {
		this.rest = rest;
		this.baseUrl = baseUrl;
	}

	public Map<Integer, CourseDto> fetchAll() {
		try {
			List<CourseDto> list = rest.exchange(baseUrl + "/api/v1/courses/allcourses", HttpMethod.GET, null,
					new ParameterizedTypeReference<List<CourseDto>>() {
					}).getBody();
			Map<Integer, CourseDto> map = new LinkedHashMap<>();
			if (list != null) {
				for (CourseDto c : list) {
					map.put(c.getCid(), c);
				}
			}
			return map;
		} catch (RestClientException e) {
			throw new ShopException(HttpStatus.SERVICE_UNAVAILABLE,
					"The course service is not reachable right now. Please try again shortly.");
		}
	}
}
