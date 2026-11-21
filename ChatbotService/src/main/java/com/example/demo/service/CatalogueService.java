package com.example.demo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.demo.dto.UserContext;
import com.example.demo.tools.ServiceClients;
import com.example.demo.tools.ToolException;
import com.fasterxml.jackson.databind.JsonNode;

// Course catalogue and "what does this user already have", for the AI features.
@Service
public class CatalogueService {

	public record CourseInfo(int id, String name, String duration, BigDecimal price, String tagline, String level, int modules) {
	}

	private final ServiceClients api;

	public CatalogueService(ServiceClients api) {
		this.api = api;
	}

	public List<CourseInfo> catalogue() {
		JsonNode all = api.course("/api/v1/courses/allcourses");
		Map<Integer, JsonNode> summaries = new HashMap<>();
		try {
			JsonNode list = api.course("/api/v1/courses/details-summary");
			if (list != null && list.isArray()) {
				for (JsonNode s : list) {
					summaries.put(s.path("cid").asInt(), s);
				}
			}
		} catch (ToolException e) {
			// courses still work without the extra facts
		}
		List<CourseInfo> out = new ArrayList<>();
		if (all != null && all.isArray()) {
			for (JsonNode c : all) {
				int id = c.path("cid").asInt();
				JsonNode p = c.path("price");
				BigDecimal price = p.isNumber() && p.decimalValue().signum() > 0 ? p.decimalValue() : null;
				JsonNode s = summaries.get(id);
				out.add(new CourseInfo(id, c.path("cname").asText(""), c.path("duration").asText(""), price,
						s == null ? "" : s.path("tagline").asText(""), s == null ? "" : s.path("level").asText(""),
						s == null ? 0 : s.path("moduleCount").asInt()));
			}
		}
		return out;
	}

	public CourseInfo course(int id) {
		for (CourseInfo c : catalogue()) {
			if (c.id() == id) {
				return c;
			}
		}
		throw new ChatException(404, "That course was not found.");
	}

	public JsonNode details(int id) {
		return api.course("/api/v1/courses/" + id + "/details");
	}

	public Set<Integer> ownedIds(UserContext user) {
		return ids(api.order("/api/v1/shop/my-courses", user.token()));
	}

	public Set<Integer> wishlistIds(UserContext user) {
		return ids(api.order("/api/v1/shop/wishlist", user.token()));
	}

	private static Set<Integer> ids(JsonNode list) {
		Set<Integer> ids = new HashSet<>();
		if (list != null && list.isArray()) {
			for (JsonNode n : list) {
				ids.add(n.path("courseId").asInt());
			}
		}
		return ids;
	}
}
