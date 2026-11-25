package com.djt.jukeanator_engine.domain.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The native mobile app calls the API cross-origin, so an error response must keep the CORS
 * headers Spring Security's CorsFilter added before the handler ran. Without them the app can't
 * read the error and only reports "Failed to fetch" (seen in production for a declined payment).
 */
class GlobalExceptionHandlerCorsTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());

  @Test
  void anErrorResponse_keepsTheCorsHeaders_butDropsOtherStaleHeaders() throws Exception {

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/add-funds");
    MockHttpServletResponse response = new MockHttpServletResponse();
    // As left by CorsFilter (plus a stale header the reset is there to clear).
    response.addHeader("Vary", "Origin");
    response.addHeader("Vary", "Access-Control-Request-Method");
    response.addHeader("Access-Control-Allow-Origin", "https://localhost");
    response.addHeader("X-Stale", "drop me");
    response.setContentType("application/javascript");

    handler.handlePayment(new PaymentException("Payment declined: card refused"), request, response);

    assertEquals(402, response.getStatus());
    assertEquals("https://localhost", response.getHeader("Access-Control-Allow-Origin"));
    assertEquals(List.of("Origin", "Access-Control-Request-Method"), response.getHeaders("Vary"));
    assertNull(response.getHeader("X-Stale"));
    assertTrue(response.getContentType().startsWith("application/json"));
    assertTrue(response.getContentAsString().contains("Payment declined: card refused"));
  }

  @Test
  void anErrorResponseWithoutCors_isUnchanged() throws Exception {

    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handlePayment(new PaymentException("declined"),
        new MockHttpServletRequest("POST", "/api/users/add-funds"), response);

    assertEquals(402, response.getStatus());
    assertNull(response.getHeader("Access-Control-Allow-Origin"));
  }
}
