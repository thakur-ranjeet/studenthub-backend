package com.example.demo.brain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One-shot AI calls for the AI features (course drafts, ranking, quizzes, explanations).
 * The provider is chosen with ai.provider:  anthropic | gemini | ollama.
 * "structured" makes the model answer with JSON that follows a schema, so the result is always parseable.
 * (The chat bubble's tool-using brain, LlmBrain, still uses Anthropic only; with another provider the chat stays in basic mode.)
 */
@Component
public class LlmClient {

	private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
	private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
	private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

	/** Tried in this order (after the configured model) when Google says a model does not exist or is retired. */
	private static final List<String> GEMINI_FALLBACKS = List.of("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash");

	/** Which model size a request wants. Each provider maps it to its own model name. */
	public static final String QUALITY = "quality";
	public static final String FAST = "fast";

	private final RestTemplate rest;
	private final ObjectMapper om;
	private final String provider;
	private final String anthropicKey;
	private final String anthropicQuality;
	private final String anthropicFast;
	private final String geminiKey;
	private final String geminiModel;
	private volatile String geminiWorkingModel; // the model that last answered, so we do not retry dead ones every time
	private volatile String lastError = "";
	private final String ollamaUrl;
	private final String ollamaModel;

	public LlmClient(@Qualifier("llmRest") RestTemplate rest, ObjectMapper om,
			@Value("${ai.provider:}") String provider,
			@Value("${chatbot.anthropic.api-key:}") String anthropicKey,
			@Value("${ai.anthropic.model-quality:claude-sonnet-5-5}") String anthropicQuality,
			@Value("${ai.anthropic.model-fast:claude-haiku-5-5}") String anthropicFast,
			@Value("${ai.gemini.api-key:}") String geminiKey,
			@Value("${ai.gemini.model:gemini-3.8-flash}") String geminiModel,
			@Value("${ai.ollama.url:http://localhost:11434}") String ollamaUrl,
			@Value("${ai.ollama.model:llama3.1}") String ollamaModel) {
		this.rest = rest;
		this.om = om;
		this.anthropicKey = clean(anthropicKey);
		this.geminiKey = clean(geminiKey);
		// AI_PROVIDER not set: pick the provider that has a key. (Before, a Gemini key was silently ignored.)
		String chosen = clean(provider).toLowerCase();
		if (chosen.isEmpty()) {
			chosen = this.anthropicKey.isEmpty() && !this.geminiKey.isEmpty() ? "gemini" : "anthropic";
		}
		this.provider = chosen;
		this.anthropicQuality = clean(anthropicQuality);
		this.anthropicFast = clean(anthropicFast);
		this.geminiModel = clean(geminiModel);
		String url = clean(ollamaUrl);
		this.ollamaUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
		this.ollamaModel = clean(ollamaModel);
		log.info("AI provider: {} -> {}", this.provider, available() ? "ready" : "NOT ready: " + setupHint());
	}

	public boolean available() {
		return switch (provider) {
			case "anthropic" -> !anthropicKey.isEmpty();
			case "gemini" -> !geminiKey.isEmpty();
			case "ollama" -> true; // cannot know if it is running until we call it
			default -> false;
		};
	}

	/** For the admin status endpoint. Never contains a key. */
	public String provider() {
		return provider;
	}

	public String activeModel() {
		return switch (provider) {
			case "gemini" -> geminiWorkingModel != null ? geminiWorkingModel : geminiModel;
			case "ollama" -> ollamaModel;
			default -> anthropicFast + " / " + anthropicQuality;
		};
	}

	/** The last reason an AI call failed (empty if the last call worked). */
	public String lastError() {
		return lastError;
	}

	private String setupHint() {
		return switch (provider) {
			case "anthropic" -> "set ANTHROPIC_API_KEY";
			case "gemini" -> "set GEMINI_API_KEY";
			default -> "ai.provider must be anthropic, gemini or ollama (found '" + provider + "')";
		};
	}

	// ------------------------------------------------------------------ public API

	/** Returns the JSON object the model produced, following inputSchema. */
	public JsonNode structured(String tier, String system, String user, String toolName, String toolDescription,
			ObjectNode inputSchema, int maxTokens) {
		return switch (provider) {
			case "gemini" -> parseJson(geminiText(system, user, inputSchema, maxTokens, FAST.equals(tier)));
			case "ollama" -> parseJson(ollamaText(system + "\nReply with JSON only.", user, inputSchema, maxTokens));
			case "anthropic" -> anthropicStructured(anthropicModel(tier), system, user, toolName, toolDescription, inputSchema, maxTokens);
			default -> throw new LlmUnavailableException("unknown provider");
		};
	}

	/** Plain text answer. */
	public String text(String tier, String system, String user, int maxTokens) {
		return switch (provider) {
			case "gemini" -> geminiText(system, user, null, maxTokens, FAST.equals(tier));
			case "ollama" -> ollamaText(system, user, null, maxTokens);
			case "anthropic" -> anthropicText(anthropicModel(tier), system, user, maxTokens);
			default -> throw new LlmUnavailableException("unknown provider");
		};
	}

	// ------------------------------------------------------------------ Anthropic

	private String anthropicModel(String tier) {
		return QUALITY.equals(tier) ? anthropicQuality : anthropicFast;
	}

	private JsonNode anthropicStructured(String model, String system, String user, String toolName, String toolDescription,
			ObjectNode inputSchema, int maxTokens) {
		ObjectNode body = anthropicBase(model, system, user, maxTokens);
		ObjectNode tool = body.putArray("tools").addObject();
		tool.put("name", toolName);
		tool.put("description", toolDescription);
		tool.set("input_schema", inputSchema);
		ObjectNode choice = body.putObject("tool_choice");
		choice.put("type", "tool");
		choice.put("name", toolName);

		JsonNode response = anthropicPost(body);
		if ("max_tokens".equals(response.path("stop_reason").asText())) {
			throw new LlmUnavailableException("answer was cut off");
		}
		for (JsonNode block : response.path("content")) {
			if ("tool_use".equals(block.path("type").asText()) && toolName.equals(block.path("name").asText())) {
				return block.path("input");
			}
		}
		throw new LlmUnavailableException("no structured answer");
	}

	private String anthropicText(String model, String system, String user, int maxTokens) {
		JsonNode response = anthropicPost(anthropicBase(model, system, user, maxTokens));
		StringBuilder sb = new StringBuilder();
		for (JsonNode block : response.path("content")) {
			if ("text".equals(block.path("type").asText())) {
				sb.append(block.path("text").asText());
			}
		}
		return requireText(sb.toString());
	}

	private ObjectNode anthropicBase(String model, String system, String user, int maxTokens) {
		ObjectNode body = om.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", maxTokens);
		body.put("system", system);
		ObjectNode m = body.putArray("messages").addObject();
		m.put("role", "user");
		m.put("content", user);
		return body;
	}

	private JsonNode anthropicPost(ObjectNode body) {
		HttpHeaders headers = jsonHeaders();
		headers.set("x-api-key", anthropicKey);
		headers.set("anthropic-version", "2023-06-01");
		return post(ANTHROPIC_URL, headers, body, "Anthropic");
	}

	// ------------------------------------------------------------------ Google Gemini

	// schema == null: plain text. Otherwise the answer is JSON that follows the schema.
	private String geminiText(String system, String user, ObjectNode schema, int maxTokens, boolean fast) {
		ObjectNode body = om.createObjectNode();
		body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
		ObjectNode turn = body.putArray("contents").addObject();
		turn.put("role", "user");
		turn.putArray("parts").addObject().put("text", user);
		ObjectNode config = body.putObject("generationConfig");
		// thinking models spend part of this budget before they answer, so leave room
		config.put("maxOutputTokens", Math.max(maxTokens * 3, 4096));
		if (fast) {
			// ranking and short explanations need little thinking; this keeps the answer quick (dropped automatically if the model rejects it)
			config.putObject("thinkingConfig").put("thinkingLevel", "low");
		}
		if (schema != null) {
			config.put("responseMimeType", "application/json");
			config.set("responseJsonSchema", schema);
		}
		HttpHeaders headers = jsonHeaders();
		headers.set("x-goog-api-key", geminiKey);

		JsonNode response = geminiPost(headers, body);
		JsonNode candidate = response.path("candidates").path(0);
		if ("MAX_TOKENS".equals(candidate.path("finishReason").asText())) {
			throw new LlmUnavailableException("answer was cut off");
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode part : candidate.path("content").path("parts")) {
			if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
				sb.append(part.path("text").asText());
			}
		}
		return requireText(sb.toString());
	}

	// Tries the configured model first. If Google answers 404 (model unknown or retired) the next model is tried.
	// Any other error (401 key rejected, 403, 429 quota, 5xx) is not a model problem, so it stops at once.
	private JsonNode geminiPost(HttpHeaders headers, ObjectNode body) {
		Set<String> models = new LinkedHashSet<>();
		if (geminiWorkingModel != null) {
			models.add(geminiWorkingModel);
		}
		models.add(geminiModel);
		models.addAll(GEMINI_FALLBACKS);

		LlmUnavailableException last = null;
		for (String model : new ArrayList<>(models)) {
			try {
				JsonNode response;
				try {
					response = post(GEMINI_URL + model + ":generateContent", headers, body, "Gemini (" + model + ")");
				} catch (BadRequestException e) {
					if (!dropThinking(body)) {
						throw e;
					}
					log.info("Gemini rejected the thinking setting for {}, retrying without it", model);
					response = post(GEMINI_URL + model + ":generateContent", headers, body, "Gemini (" + model + ")");
				}
				if (!model.equals(geminiWorkingModel)) {
					log.info("Gemini model in use: {}", model);
				}
				geminiWorkingModel = model;
				return response;
			} catch (ModelNotFoundException e) {
				last = e;
				if (model.equals(geminiWorkingModel)) {
					geminiWorkingModel = null;
				}
			}
		}
		throw last != null ? last : new LlmUnavailableException("no Gemini model available");
	}

	private static boolean dropThinking(ObjectNode body) {
		JsonNode gc = body.path("generationConfig");
		if (gc.isObject() && gc.has("thinkingConfig")) {
			((ObjectNode) gc).remove("thinkingConfig");
			return true;
		}
		return false;
	}

	/** Google said 400 (bad request). */
	private static final class BadRequestException extends LlmUnavailableException {
		private static final long serialVersionUID = 1L;

		BadRequestException(String message) {
			super(message);
		}
	}

	/** Google said 404: this model name does not exist (any more). Lets geminiPost try the next one. */
	private static final class ModelNotFoundException extends LlmUnavailableException {
		private static final long serialVersionUID = 1L;

		ModelNotFoundException(String message) {
			super(message);
		}
	}

	// ------------------------------------------------------------------ Ollama (runs on your own PC)

	private String ollamaText(String system, String user, ObjectNode schema, int maxTokens) {
		ObjectNode body = om.createObjectNode();
		body.put("model", ollamaModel);
		body.put("stream", false);
		ArrayNode messages = body.putArray("messages");
		messages.addObject().put("role", "system").put("content", system);
		messages.addObject().put("role", "user").put("content", user);
		ObjectNode options = body.putObject("options");
		options.put("num_predict", maxTokens);
		options.put("num_ctx", 8192);
		options.put("temperature", 0.3);
		if (schema != null) {
			body.set("format", schema);
		}

		JsonNode response = post(ollamaUrl + "/api/chat", jsonHeaders(), body, "Ollama");
		if ("length".equals(response.path("done_reason").asText())) {
			throw new LlmUnavailableException("answer was cut off");
		}
		return requireText(response.path("message").path("content").asText(""));
	}

	// ------------------------------------------------------------------ shared

	private JsonNode post(String url, HttpHeaders headers, ObjectNode body, String who) {
		try {
			JsonNode response = rest.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
			if (response == null) {
				throw new LlmUnavailableException("empty response");
			}
			lastError = "";
			return response;
		} catch (HttpStatusCodeException e) {
			// Status plus Google's / the provider's own error text. Never the key or the prompt.
			int status = e.getStatusCode().value();
			String reason = providerReason(e.getResponseBodyAsString());
			lastError = who + " answered HTTP " + status + (reason.isEmpty() ? "" : ": " + reason);
			log.warn("AI provider ({}) answered HTTP {}{}", who, status, reason.isEmpty() ? "" : ": " + reason);
			if (status == 404 && who.startsWith("Gemini")) {
				throw new ModelNotFoundException("model not found");
			}
			if (status == 400 && who.startsWith("Gemini")) {
				throw new BadRequestException("bad request");
			}
			throw new LlmUnavailableException("provider error");
		} catch (RestClientException e) {
			lastError = who + " not reachable: " + e.getClass().getSimpleName();
			log.warn("AI provider ({}) not reachable: {}{}", who, e.getClass().getSimpleName(),
					"Ollama".equals(who) ? " (is Ollama running at " + ollamaUrl + "?)" : "");
			throw new LlmUnavailableException("provider unreachable");
		}
	}

	// Gemini and Anthropic both reply { "error": { "message": "...", "status": "..." } }. Keep it short.
	private String providerReason(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		try {
			JsonNode err = om.readTree(raw).path("error");
			String status = err.path("status").asText(err.path("type").asText(""));
			String message = err.path("message").asText("");
			String text = (status.isEmpty() ? "" : status + " - ") + message;
			return text.length() <= 300 ? text : text.substring(0, 300);
		} catch (JsonProcessingException e) {
			return "";
		}
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static String requireText(String text) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty()) {
			throw new LlmUnavailableException("empty answer");
		}
		return t;
	}

	// models sometimes wrap JSON in a ```json fence even when asked not to
	private JsonNode parseJson(String text) {
		String t = text.trim();
		if (t.startsWith("```")) {
			int firstLine = t.indexOf('\n');
			int end = t.lastIndexOf("```");
			if (firstLine > 0 && end > firstLine) {
				t = t.substring(firstLine + 1, end).trim();
			}
		}
		try {
			JsonNode node = om.readTree(t);
			if (node == null || !node.isObject()) {
				throw new LlmUnavailableException("answer was not a JSON object");
			}
			return node;
		} catch (JsonProcessingException e) {
			throw new LlmUnavailableException("answer was not valid JSON");
		}
	}

	private static String clean(String s) {
		return s == null ? "" : s.trim();
	}
}
