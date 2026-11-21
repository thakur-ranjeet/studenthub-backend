package com.example.demo.tools;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.demo.dto.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The things the assistant is allowed to look up. Each tool:
 *  - is READ-ONLY (the assistant never buys, refunds, edits or deletes anything),
 *  - returns only the few fields needed to answer (no emails, no passwords, no raw records),
 *  - enforces who may use it HERE, in code. The AI is never trusted to decide permissions.
 */
@Component
public class ChatTools {

	private static final Logger log = LoggerFactory.getLogger(ChatTools.class);

	private static final int MAX_LIST = 15;
	private static final int MAX_MODULES = 30;
	private static final int MAX_LESSONS_SHOWN = 15;

	private final ServiceClients api;
	private final ObjectMapper om;

	public ChatTools(ServiceClients api, ObjectMapper om) {
		this.api = api;
		this.om = om;
	}

	/** Collects which courses an answer is about, so the app can show real links under it. */
	public static class Trace {
		private final Map<Integer, String> courses = new LinkedHashMap<>();

		void add(int id, String name) {
			courses.put(id, name);
		}

		public Map<Integer, String> courses() {
			return courses;
		}
	}

	// ------------------------------------------------------------------ what the AI is told it can use

	public ArrayNode definitions(boolean admin) {
		ArrayNode tools = om.createArrayNode();
		tools.add(tool("list_courses",
				"List the courses for sale with price, duration, level, tagline and module count. "
						+ "Use for questions like 'what courses are there', 'courses under 3000', 'anything on Java'.",
				props("query", "string", "Optional word to look for in the course name, for example 'python'.",
						"max_price_inr", "number", "Optional upper price limit in Indian rupees.")));
		tools.add(tool("get_course_details",
				"Get one course in detail: introduction, level, language, instructor, requirements, what you will learn, "
						+ "who it is for and the modules. Use for 'what is in the Python course', 'how many modules', 'is it good for beginners'. "
						+ "Give course_id or course_name.",
				props("course_id", "integer", "The course id, if known.",
						"course_name", "string", "The course name or part of it, for example 'python'.",
						"include_lessons", "boolean", "true only if the user asks for the lesson titles inside the modules.")));
		tools.add(tool("get_my_courses", "The courses the signed-in user has already bought (enrolled in).", props()));
		tools.add(tool("get_my_cart", "What is in the signed-in user's cart, with the total.", props()));
		tools.add(tool("get_my_wishlist", "The signed-in user's wishlist.", props()));
		tools.add(tool("get_my_orders",
				"The signed-in user's latest orders with total and payment status (PAID, CREATED = checkout started but not paid, "
						+ "FAILED, REFUNDED).",
				props()));
		if (admin) {
			tools.add(tool("admin_course_students",
					"ADMIN ONLY. How many students are enrolled in a course, and their names.",
					props("course_id", "integer", "The course id."), "course_id"));
			tools.add(tool("admin_sales_summary",
					"ADMIN ONLY. Number of orders by status, revenue and the best selling courses.", props()));
		}
		return tools;
	}

	// ------------------------------------------------------------------ running a tool

	/** Never throws: a failure becomes {"error": "..."} so the assistant can explain it to the user. */
	public JsonNode run(String name, JsonNode input, UserContext user, Trace trace) {
		JsonNode in = input == null ? om.createObjectNode() : input;
		try {
			return switch (name) {
				case "list_courses" -> listCourses(in, trace);
				case "get_course_details" -> courseDetails(in, trace);
				case "get_my_courses" -> myCourses(user, trace);
				case "get_my_cart" -> myCart(user);
				case "get_my_wishlist" -> myWishlist(user);
				case "get_my_orders" -> myOrders(user);
				case "admin_course_students" -> {
					requireAdmin(user);
					yield courseStudents(in);
				}
				case "admin_sales_summary" -> {
					requireAdmin(user);
					yield salesSummary(user);
				}
				default -> error("That lookup does not exist.");
			};
		} catch (ToolException e) {
			return error(e.getMessage());
		} catch (RuntimeException e) {
			log.warn("Tool {} failed", name, e);
			return error("Something went wrong while looking that up.");
		}
	}

	private static void requireAdmin(UserContext user) {
		if (!user.isAdmin()) {
			throw new ToolException("Only administrators can see that.");
		}
	}

	// ------------------------------------------------------------------ course tools

	private JsonNode listCourses(JsonNode in, Trace trace) {
		String q = in.path("query").asText("").trim().toLowerCase(Locale.ROOT);
		double max = in.path("max_price_inr").asDouble(0);
		JsonNode all = api.course("/api/v1/courses/allcourses");
		Map<Integer, JsonNode> summaries = summaries();

		ArrayNode out = om.createArrayNode();
		int matching = 0;
		for (JsonNode c : iterable(all)) {
			String name = c.path("cname").asText("");
			if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			BigDecimal price = price(c);
			if (max > 0 && (price == null || price.doubleValue() > max)) {
				continue;
			}
			matching++;
			if (out.size() < MAX_LIST) {
				int id = c.path("cid").asInt();
				ObjectNode o = out.addObject();
				o.put("id", id);
				o.put("name", name);
				o.put("duration", c.path("duration").asText(""));
				putPrice(o, price);
				JsonNode s = summaries.get(id);
				if (s != null) {
					putIfText(o, "level", s.path("level"));
					putIfText(o, "tagline", s.path("tagline"));
					o.put("modules", s.path("moduleCount").asInt());
				}
			}
		}
		// links under the answer only when the list is short enough to be about specific courses
		if (out.size() <= 3) {
			for (JsonNode o : out) {
				trace.add(o.path("id").asInt(), o.path("name").asText(""));
			}
		}
		ObjectNode result = om.createObjectNode();
		result.put("total_matching", matching);
		result.set("courses", out);
		return result;
	}

	private JsonNode courseDetails(JsonNode in, Trace trace) {
		JsonNode all = api.course("/api/v1/courses/allcourses");
		JsonNode course = null;

		if (in.hasNonNull("course_id")) {
			int id = in.get("course_id").asInt(-1);
			for (JsonNode c : iterable(all)) {
				if (c.path("cid").asInt(-2) == id) {
					course = c;
				}
			}
		} else {
			String q = in.path("course_name").asText("").trim().toLowerCase(Locale.ROOT);
			if (q.isEmpty()) {
				return error("Ask the user which course they mean.");
			}
			List<JsonNode> contains = new ArrayList<>();
			for (JsonNode c : iterable(all)) {
				String n = c.path("cname").asText("").toLowerCase(Locale.ROOT);
				if (n.equals(q)) {
					contains.clear();
					contains.add(c);
					break;
				}
				if (n.contains(q) || q.contains(n) && !n.isEmpty()) {
					contains.add(c);
				}
			}
			if (contains.size() > 1) {
				ObjectNode r = om.createObjectNode();
				r.put("note", "Several courses match. Ask the user which one they mean.");
				ArrayNode m = r.putArray("matches");
				for (JsonNode c : contains.subList(0, Math.min(contains.size(), 8))) {
					ObjectNode o = m.addObject();
					o.put("id", c.path("cid").asInt());
					o.put("name", c.path("cname").asText(""));
				}
				return r;
			}
			if (contains.size() == 1) {
				course = contains.get(0);
			}
		}

		if (course == null) {
			ObjectNode r = om.createObjectNode();
			r.put("error", "No course matches that.");
			ArrayNode names = r.putArray("available_courses");
			int n = 0;
			for (JsonNode c : iterable(all)) {
				if (n++ < MAX_LIST) {
					names.add(c.path("cname").asText(""));
				}
			}
			return r;
		}

		int cid = course.path("cid").asInt();
		trace.add(cid, course.path("cname").asText(""));
		ObjectNode out = om.createObjectNode();
		out.put("id", cid);
		out.put("name", course.path("cname").asText(""));
		out.put("duration", course.path("duration").asText(""));
		putPrice(out, price(course));

		JsonNode d = api.course("/api/v1/courses/" + cid + "/details");
		boolean hasContent = d != null && (d.hasNonNull("description") || d.hasNonNull("tagline")
				|| d.path("modules").size() > 0 || d.path("outcomes").size() > 0);
		if (!hasContent) {
			out.put("details", "The detailed course page has not been published yet.");
			return out;
		}
		putIfText(out, "tagline", d.path("tagline"));
		putIfText(out, "level", d.path("level"));
		putIfText(out, "language", d.path("language"));
		putIfText(out, "instructor", d.path("instructor"));
		if (d.hasNonNull("description")) {
			out.put("introduction", clip(d.path("description").asText(), 700));
		}
		if (d.hasNonNull("prerequisites")) {
			out.put("requirements", clip(d.path("prerequisites").asText(), 300));
		}
		out.set("what_you_will_learn", firstStrings(d.path("outcomes"), 8, 160));
		out.set("who_it_is_for", firstStrings(d.path("audience"), 5, 160));

		boolean lessons = in.path("include_lessons").asBoolean(false);
		JsonNode modules = d.path("modules");
		int lessonTotal = 0;
		ArrayNode mo = out.putArray("modules");
		int number = 0;
		for (JsonNode m : iterable(modules)) {
			number++;
			int count = m.path("lessons").size();
			lessonTotal += count;
			if (number <= MAX_MODULES) {
				ObjectNode o = mo.addObject();
				o.put("no", number);
				o.put("title", m.path("title").asText(""));
				putIfText(o, "duration", m.path("duration"));
				o.put("lesson_count", count);
				if (lessons) {
					o.set("lessons", firstStrings(m.path("lessons"), MAX_LESSONS_SHOWN, 120));
				}
			}
		}
		out.put("module_count", modules.size());
		out.put("lesson_count", lessonTotal);
		return out;
	}

	private Map<Integer, JsonNode> summaries() {
		Map<Integer, JsonNode> map = new HashMap<>();
		try {
			for (JsonNode s : iterable(api.course("/api/v1/courses/details-summary"))) {
				map.put(s.path("cid").asInt(), s);
			}
		} catch (ToolException e) {
			// the list still works without the extra facts
		}
		return map;
	}

	// ------------------------------------------------------------------ the signed-in user's own data

	private JsonNode myCourses(UserContext user, Trace trace) {
		JsonNode list = api.order("/api/v1/shop/my-courses", user.token());
		ArrayNode out = om.createArrayNode();
		for (JsonNode e : iterable(list)) {
			ObjectNode o = out.addObject();
			o.put("id", e.path("courseId").asInt());
			o.put("name", e.path("cname").asText(""));
			o.put("duration", e.path("duration").asText(""));
			o.put("enrolled_on", date(e.path("enrolledAt")));
		}
		if (out.size() <= 3) {
			for (JsonNode o : out) {
				trace.add(o.path("id").asInt(), o.path("name").asText(""));
			}
		}
		ObjectNode r = om.createObjectNode();
		r.put("count", out.size());
		r.set("courses", out);
		return r;
	}

	private JsonNode myCart(UserContext user) {
		JsonNode cart = api.order("/api/v1/shop/cart", user.token());
		ObjectNode r = om.createObjectNode();
		r.put("count", cart.path("count").asInt());
		r.put("total_inr", cart.path("total").decimalValue());
		ArrayNode items = r.putArray("items");
		for (JsonNode i : iterable(cart.path("items"))) {
			ObjectNode o = items.addObject();
			o.put("id", i.path("courseId").asInt());
			o.put("name", i.path("cname").asText(""));
			putPrice(o, price(i));
			o.put("still_available", i.path("available").asBoolean(true));
		}
		return r;
	}

	private JsonNode myWishlist(UserContext user) {
		JsonNode list = api.order("/api/v1/shop/wishlist", user.token());
		ObjectNode r = om.createObjectNode();
		ArrayNode items = r.putArray("items");
		for (JsonNode i : iterable(list)) {
			ObjectNode o = items.addObject();
			o.put("id", i.path("courseId").asInt());
			o.put("name", i.path("cname").asText(""));
			putPrice(o, price(i));
			o.put("in_cart", i.path("inCart").asBoolean(false));
		}
		r.put("count", items.size());
		return r;
	}

	private JsonNode myOrders(UserContext user) {
		JsonNode list = api.order("/api/v1/shop/orders", user.token());
		List<JsonNode> orders = new ArrayList<>();
		iterable(list).forEach(orders::add);
		orders.sort(Comparator.comparing((JsonNode o) -> o.path("createdAt").asText("")).reversed());

		ObjectNode r = om.createObjectNode();
		r.put("total_orders", orders.size());
		ArrayNode out = r.putArray("latest_orders");
		for (JsonNode o : orders.subList(0, Math.min(orders.size(), 5))) {
			ObjectNode x = out.addObject();
			x.put("order_id", o.path("id").asLong());
			x.put("total_inr", o.path("total").decimalValue());
			x.put("status", o.path("status").asText(""));
			putIfText(x, "refund_status", o.path("refundStatus"));
			x.put("date", date(o.path("createdAt")));
			ArrayNode names = x.putArray("courses");
			for (JsonNode i : iterable(o.path("items"))) {
				names.add(i.path("cname").asText(""));
			}
		}
		return r;
	}

	// ------------------------------------------------------------------ admin tools

	private JsonNode courseStudents(JsonNode in) {
		int cid = in.path("course_id").asInt(-1);
		if (cid <= 0) {
			return error("A valid course id is needed.");
		}
		JsonNode list = api.student("/api/v1/students/allStudentofcrs/" + cid);
		ObjectNode r = om.createObjectNode();
		r.put("course_id", cid);
		r.put("student_count", list == null ? 0 : list.size());
		ArrayNode names = r.putArray("students");
		for (JsonNode s : iterable(list)) {
			if (names.size() >= 25) {
				break;
			}
			// only id and name: the student record also holds an e-mail and a password field, which must never reach the chat
			ObjectNode o = names.addObject();
			o.put("id", s.path("sid").asInt());
			o.put("name", s.path("sname").asText(""));
		}
		return r;
	}

	private JsonNode salesSummary(UserContext user) {
		JsonNode orders = api.order("/api/v1/shop/admin/orders", user.token());
		Map<String, Integer> byStatus = new LinkedHashMap<>();
		Map<String, Integer> sold = new HashMap<>();
		BigDecimal paid = BigDecimal.ZERO;
		BigDecimal refunded = BigDecimal.ZERO;
		for (JsonNode o : iterable(orders)) {
			String status = o.path("status").asText("UNKNOWN");
			byStatus.merge(status, 1, Integer::sum);
			if ("PAID".equals(status)) {
				paid = paid.add(o.path("total").decimalValue());
				for (JsonNode i : iterable(o.path("items"))) {
					sold.merge(i.path("cname").asText("?"), 1, Integer::sum);
				}
			} else if ("REFUNDED".equals(status)) {
				refunded = refunded.add(o.path("total").decimalValue());
			}
		}
		ObjectNode r = om.createObjectNode();
		ObjectNode counts = r.putObject("orders_by_status");
		for (Map.Entry<String, Integer> e : byStatus.entrySet()) {
			counts.put(e.getKey(), e.getValue().intValue());
		}
		r.put("revenue_paid_inr", paid);
		r.put("refunded_inr", refunded);
		ArrayNode top = r.putArray("best_selling_courses");
		sold.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(3).forEach(e -> {
			ObjectNode o = top.addObject();
			o.put("course", e.getKey());
			o.put("sold", e.getValue());
		});
		return r;
	}

	// ------------------------------------------------------------------ small helpers

	private ObjectNode error(String message) {
		ObjectNode o = om.createObjectNode();
		o.put("error", message);
		return o;
	}

	private ObjectNode tool(String name, String description, ObjectNode properties, String... required) {
		ObjectNode t = om.createObjectNode();
		t.put("name", name);
		t.put("description", description);
		ObjectNode schema = t.putObject("input_schema");
		schema.put("type", "object");
		schema.set("properties", properties);
		ArrayNode req = schema.putArray("required");
		for (String r : required) {
			req.add(r);
		}
		return t;
	}

	/** props("name", "type", "description", ...) in groups of three. */
	private ObjectNode props(String... triples) {
		ObjectNode p = om.createObjectNode();
		for (int i = 0; i + 2 < triples.length; i += 3) {
			ObjectNode f = p.putObject(triples[i]);
			f.put("type", triples[i + 1]);
			f.put("description", triples[i + 2]);
		}
		return p;
	}

	private static Iterable<JsonNode> iterable(JsonNode n) {
		return n == null || !n.isArray() ? List.of() : n;
	}

	private static BigDecimal price(JsonNode c) {
		JsonNode p = c.path("price");
		return p.isNumber() && p.decimalValue().signum() > 0 ? p.decimalValue() : null;
	}

	private static void putPrice(ObjectNode o, BigDecimal price) {
		if (price == null) {
			o.put("price_inr", "not for sale yet");
		} else {
			o.put("price_inr", price);
		}
	}

	private static void putIfText(ObjectNode o, String field, JsonNode value) {
		if (value != null && value.isTextual() && !value.asText().isBlank()) {
			o.put(field, value.asText());
		}
	}

	private ArrayNode firstStrings(JsonNode list, int max, int maxChars) {
		ArrayNode out = om.createArrayNode();
		for (JsonNode s : iterable(list)) {
			if (out.size() >= max) {
				break;
			}
			out.add(clip(s.asText(""), maxChars));
		}
		return out;
	}

	private static String clip(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max).trim() + "…";
	}

	private static String date(JsonNode instant) {
		String t = instant.asText("");
		return t.length() >= 10 ? t.substring(0, 10) : t;
	}
}
