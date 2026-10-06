package com.djt.jukeanator_engine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Calls the engine's REST API exactly as the Web/Mobile UI's {@code api()} helper in
 * {@code static/js/app.js} does: a JSON body, {@code Content-Type: application/json}, and the
 * patron's JWT as a {@code Bearer} token when logged in (none when anonymous). Requests go through
 * the full servlet filter chain -- {@code JwtAuthenticationFilter} and {@code SecurityConfig}'s
 * rules included -- via {@link MockMvc}, so no port is opened.
 *
 * <p>Every response is returned rather than asserted, since the UI depends on error statuses
 * (401/402/403/404/409) as much as on successful ones; {@link Response#expect} asserts a status.
 */
final class WebUiTestClient {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  WebUiTestClient(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  Response get(String token, String path, Object... uriVariables) {
    return send(MockMvcRequestBuilders.get(path, uriVariables), token, null, Map.of());
  }

  Response post(String token, String path, Object body, Object... uriVariables) {
    return send(MockMvcRequestBuilders.post(path, uriVariables), token, body, Map.of());
  }

  Response postWithHeaders(String token, Map<String, String> headers, String path, Object body,
      Object... uriVariables) {
    return send(MockMvcRequestBuilders.post(path, uriVariables), token, body, headers);
  }

  Response put(String token, String path, Object body, Object... uriVariables) {
    return send(MockMvcRequestBuilders.put(path, uriVariables), token, body, Map.of());
  }

  Response delete(String token, String path, Object body, Object... uriVariables) {
    return send(MockMvcRequestBuilders.delete(path, uriVariables), token, body, Map.of());
  }

  /** A song's identity, from a SongDto, a SongIdentifier, a queue entry or an eligibility row. */
  static String key(JsonNode node) {
    JsonNode song = node.has("song") ? node.path("song") : node;
    return song.path("albumId").asInt() + "/" + song.path("songId").asInt();
  }

  static List<String> keys(JsonNode array) {
    return list(array).stream().map(WebUiTestClient::key).toList();
  }

  static List<JsonNode> list(JsonNode array) {
    List<JsonNode> items = new ArrayList<>();
    array.forEach(items::add);
    return items;
  }

  static List<String> texts(JsonNode array) {
    return list(array).stream().map(JsonNode::asText).toList();
  }

  /** Unique per run: the user stores outlive a single test. */
  static String uniqueEmail(String name) {
    return name + "+" + System.nanoTime() + "@example.com";
  }

  /** Serializes a request body the same way {@code JSON.stringify} would. */
  String toJson(Object body) {
    try {
      return body instanceof String text ? text : objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new IllegalStateException("Could not serialize request body: " + body, e);
    }
  }

  private Response send(MockHttpServletRequestBuilder request, String token, Object body,
      Map<String, String> headers) {

    request.contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    headers.forEach(request::header);
    if (body != null) {
      request.content(toJson(body));
    }
    try {
      MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
      return new Response(response.getStatus(),
          response.getContentAsString(StandardCharsets.UTF_8), response, objectMapper);
    } catch (Exception e) {
      throw new IllegalStateException("Request failed to execute", e);
    }
  }

  /** One HTTP response, as the UI's {@code fetch()} would see it. */
  record Response(int status, String body, MockHttpServletResponse raw, ObjectMapper objectMapper) {

    /** Asserts the status, reporting the body (usually an {@code ApiError}) when it differs. */
    Response expect(int expectedStatus) {
      assertEquals(expectedStatus, status, () -> "Unexpected status; body: " + body);
      return this;
    }

    /** The body as JSON, or {@code null} JSON when empty -- {@code api()} returns null there. */
    JsonNode json() {
      if (body == null || body.isBlank()) {
        return NullNode.getInstance();
      }
      try {
        return objectMapper.readTree(body);
      } catch (Exception e) {
        throw new IllegalStateException("Response body is not JSON: " + body, e);
      }
    }

    /** The {@code ApiError.error} field -- the exception name app.js switches on. */
    String error() {
      return json().path("error").asText(null);
    }

    boolean isSuccessful() {
      return status >= 200 && status < 300;
    }

    byte[] bytes() {
      return raw.getContentAsByteArray();
    }
  }
}
