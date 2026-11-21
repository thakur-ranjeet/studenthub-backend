package com.example.demo.brain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.demo.dto.UserContext;
import com.example.demo.tools.ChatTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Basic mode: works with no AI key. It understands a fixed set of questions by keywords and uses exactly the same
 * lookups (and the same permission checks) as AI mode. Also used when the AI provider is down.
 */
@Component
public class RuleBrain {

	private static final Pattern COURSE_ID = Pattern.compile("(?:course\\s*#?|#)(\\d{1,6})");
	private static final Pattern LIMIT = Pattern.compile("(?:under|below|less than|within|upto|up to|max)\\D{0,6}(\\d[\\d,]*)");

	private final ChatTools tools;
	private final ObjectMapper om;

	public RuleBrain(ChatTools tools, ObjectMapper om) {
		this.tools = tools;
		this.om = om;
	}

	public String answer(UserContext user, String message, ChatTools.Trace trace) {
		String m = message.toLowerCase(Locale.ROOT).trim();

		if (m.matches("^(hi|hii|hello|hey|help)\\b.*") || m.contains("what can you do")) {
			return help(user);
		}
		if (has(m, "sales", "revenue", "earning")) {
			return user.isAdmin() ? sales(run("admin_sales_summary", om.createObjectNode(), user, trace))
					: "Only administrators can see sales figures.";
		}
		if (has(m, "students in", "students of", "how many students", "students enrolled", "list students", "all students", "student count")) {
			if (!user.isAdmin()) {
				return "Only administrators can see student information.";
			}
			Integer id = courseIdIn(m, user, trace);
			return id == null ? "Which course? For example: students in course 1."
					: students(run("admin_course_students", om.createObjectNode().put("course_id", id), user, trace));
		}
		if (has(m, "cart", "basket")) {
			return cart(run("get_my_cart", om.createObjectNode(), user, trace));
		}
		if (has(m, "wishlist", "wish list")) {
			return wishlist(run("get_my_wishlist", om.createObjectNode(), user, trace));
		}
		if (has(m, "order", "payment", "receipt", "refund", "paid")) {
			return orders(run("get_my_orders", om.createObjectNode(), user, trace));
		}
		if (has(m, "my course", "enrolled", "purchased", "bought", "my learning")) {
			return myCourses(run("get_my_courses", om.createObjectNode(), user, trace));
		}

		Integer id = courseIdIn(m, user, trace);
		if (id != null) {
			boolean lessons = has(m, "lesson");
			JsonNode d = run("get_course_details", om.createObjectNode().put("course_id", id).put("include_lessons", lessons), user, trace);
			if (has(m, "price", "cost", "fee", "how much", "worth", "afford", "budget")) {
				return price(d);
			}
			if (has(m, "module", "syllabus", "curriculum", "lesson", "topic", "chapter", "cover", "content")) {
				return modules(d, lessons);
			}
			return overview(d);
		}

		if (has(m, "course", "available", "offer", "list", "cheap", "catalog", "learn")) {
			var args = om.createObjectNode();
			Matcher lim = LIMIT.matcher(m);
			if (lim.find()) {
				args.put("max_price_inr", Double.parseDouble(lim.group(1).replace(",", "")));
			}
			return courseList(run("list_courses", args, user, trace));
		}
		return "I did not understand that.\n\n" + help(user);
	}

	// ------------------------------------------------------------------ finding the course the user means

	private Integer courseIdIn(String m, UserContext user, ChatTools.Trace trace) {
		Matcher byId = COURSE_ID.matcher(m);
		if (byId.find()) {
			return Integer.valueOf(byId.group(1));
		}
		JsonNode all = run("list_courses", om.createObjectNode(), user, new ChatTools.Trace());
		Integer best = null;
		int bestLen = 0;
		for (JsonNode c : all.path("courses")) {
			String name = c.path("name").asText("").toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && m.contains(name) && name.length() > bestLen) {
				best = c.path("id").asInt();
				bestLen = name.length();
			}
		}
		return best;
	}

	private JsonNode run(String tool, JsonNode args, UserContext user, ChatTools.Trace trace) {
		return tools.run(tool, args, user, trace);
	}

	// ------------------------------------------------------------------ wording of the answers

	private String help(UserContext user) {
		String text = "I can help with:\n- the courses we offer and their prices\n- what is inside a course (modules, level, requirements), for example: modules in Python\n"
				+ "- your cart, wishlist, orders and courses";
		return user.isAdmin() ? text + "\n- students in a course (for example: students in course 1) and sales" : text;
	}

	private String courseList(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		if (r.path("courses").isEmpty()) {
			return "No course matches that.";
		}
		StringBuilder sb = new StringBuilder("Courses (" + r.path("total_matching").asInt() + "):");
		for (JsonNode c : r.path("courses")) {
			sb.append("\n- ").append(c.path("name").asText()).append(" · ").append(money(c.path("price_inr")));
			if (!c.path("duration").asText("").isEmpty()) {
				sb.append(" · ").append(c.path("duration").asText());
			}
			if (c.has("level")) {
				sb.append(" · ").append(c.path("level").asText());
			}
		}
		return sb.append("\n\nAsk about any of them, for example: modules in ").append(r.path("courses").get(0).path("name").asText()).toString();
	}

	private String overview(JsonNode d) {
		if (d.has("error") || d.has("matches")) {
			return d.path("error").asText("Which course do you mean?");
		}
		StringBuilder sb = new StringBuilder(d.path("name").asText()).append(" · ").append(money(d.path("price_inr")));
		if (!d.path("duration").asText("").isEmpty()) {
			sb.append(" · ").append(d.path("duration").asText());
		}
		if (d.has("level")) {
			sb.append(" · ").append(d.path("level").asText());
		}
		if (d.has("details")) {
			return sb.append("\n").append(d.path("details").asText()).toString();
		}
		if (d.has("tagline")) {
			sb.append("\n").append(d.path("tagline").asText());
		}
		if (d.has("introduction")) {
			String intro = d.path("introduction").asText();
			sb.append("\n\n").append(intro.length() > 320 ? intro.substring(0, 320).trim() + "…" : intro);
		}
		sb.append("\n\n").append(d.path("module_count").asInt()).append(" modules, ").append(d.path("lesson_count").asInt())
				.append(" lessons. Ask for the modules to see them.");
		return sb.toString();
	}

	private String modules(JsonNode d, boolean lessons) {
		if (d.has("error") || d.has("matches")) {
			return d.path("error").asText("Which course do you mean?");
		}
		if (d.path("modules").isEmpty()) {
			return d.path("name").asText("This course") + " does not have its modules published yet.";
		}
		StringBuilder sb = new StringBuilder(d.path("name").asText() + " has " + d.path("module_count").asInt() + " modules:");
		for (JsonNode m : d.path("modules")) {
			sb.append("\n").append(m.path("no").asInt()).append(". ").append(m.path("title").asText());
			if (m.has("duration")) {
				sb.append(" (").append(m.path("duration").asText()).append(")");
			}
			sb.append(" · ").append(m.path("lesson_count").asInt()).append(" lessons");
			if (lessons) {
				for (JsonNode l : m.path("lessons")) {
					sb.append("\n   - ").append(l.asText());
				}
			}
		}
		return sb.toString();
	}

	private String price(JsonNode d) {
		if (d.has("error") || d.has("matches")) {
			return d.path("error").asText("Which course do you mean?");
		}
		StringBuilder sb = new StringBuilder(d.path("name").asText()).append(" costs ").append(money(d.path("price_inr"))).append(".");
		sb.append("\nYou get: ").append(d.path("module_count").asInt()).append(" modules, ").append(d.path("lesson_count").asInt()).append(" lessons");
		if (!d.path("duration").asText("").isEmpty()) {
			sb.append(", ").append(d.path("duration").asText());
		}
		if (d.has("level")) {
			sb.append(", level: ").append(d.path("level").asText());
		}
		if (d.has("requirements")) {
			sb.append(".\nRequirements: ").append(d.path("requirements").asText());
		}
		return sb.toString();
	}

	private String cart(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		if (r.path("count").asInt() == 0) {
			return "Your cart is empty.";
		}
		StringBuilder sb = new StringBuilder("Your cart has " + r.path("count").asInt() + " course(s), total " + money(r.path("total_inr")) + ":");
		for (JsonNode i : r.path("items")) {
			sb.append("\n- ").append(i.path("name").asText()).append(" · ").append(money(i.path("price_inr")));
		}
		return sb.toString();
	}

	private String wishlist(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		if (r.path("count").asInt() == 0) {
			return "Your wishlist is empty.";
		}
		StringBuilder sb = new StringBuilder("Your wishlist:");
		for (JsonNode i : r.path("items")) {
			sb.append("\n- ").append(i.path("name").asText()).append(" · ").append(money(i.path("price_inr")));
		}
		return sb.toString();
	}

	private String orders(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		if (r.path("total_orders").asInt() == 0) {
			return "You have no orders yet.";
		}
		StringBuilder sb = new StringBuilder("You have " + r.path("total_orders").asInt() + " order(s). Latest:");
		boolean unpaid = false;
		for (JsonNode o : r.path("latest_orders")) {
			String status = o.path("status").asText();
			unpaid |= "CREATED".equals(status);
			sb.append("\n- #").append(o.path("order_id").asLong()).append(" · ").append(money(o.path("total_inr"))).append(" · ")
					.append(status).append(" · ").append(o.path("date").asText());
			StringBuilder names = new StringBuilder();
			for (JsonNode n : o.path("courses")) {
				names.append(names.length() == 0 ? "" : ", ").append(n.asText());
			}
			if (names.length() > 0) {
				sb.append(" · ").append(names);
			}
		}
		return unpaid ? sb.append("\n\nCREATED means checkout was started but not paid.").toString() : sb.toString();
	}

	private String myCourses(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		if (r.path("count").asInt() == 0) {
			return "You have not bought any course yet.";
		}
		StringBuilder sb = new StringBuilder("Your courses:");
		for (JsonNode c : r.path("courses")) {
			sb.append("\n- ").append(c.path("name").asText()).append(" · since ").append(c.path("enrolled_on").asText());
		}
		return sb.toString();
	}

	private String students(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		StringBuilder sb = new StringBuilder("Course " + r.path("course_id").asInt() + " has " + r.path("student_count").asInt() + " student(s)");
		if (r.path("students").isEmpty()) {
			return sb.append(".").toString();
		}
		sb.append(":");
		for (JsonNode s : r.path("students")) {
			sb.append("\n- ").append(s.path("name").asText());
		}
		return sb.toString();
	}

	private String sales(JsonNode r) {
		if (r.has("error")) {
			return r.path("error").asText();
		}
		StringBuilder sb = new StringBuilder("Paid revenue: " + money(r.path("revenue_paid_inr")) + " (refunded: " + money(r.path("refunded_inr")) + ").\nOrders:");
		r.path("orders_by_status").fields().forEachRemaining(e -> sb.append(" ").append(e.getKey()).append(" ").append(e.getValue().asInt()).append(";"));
		for (JsonNode c : r.path("best_selling_courses")) {
			sb.append("\n- ").append(c.path("course").asText()).append(": ").append(c.path("sold").asInt()).append(" sold");
		}
		return sb.toString();
	}

	private static String money(JsonNode n) {
		if (n.isNumber()) {
			BigDecimal v = n.decimalValue();
			return v.stripTrailingZeros().scale() <= 0 ? String.format(Locale.US, "₹%,d", v.longValue()) : String.format(Locale.US, "₹%,.2f", v);
		}
		return n.asText("price not set");
	}

	private static boolean has(String m, String... words) {
		for (String w : words) {
			if (m.contains(w)) {
				return true;
			}
		}
		return false;
	}
}
