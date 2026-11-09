package com.djt.jukeanator_engine.domain.license.legacy;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Text constants and encoding rules of the legacy {@code /FrontController} protocol. The trailing
 * {@code a} padding is part of the protocol: legacy clients compare these strings verbatim.
 */
public final class LegacyResponses {

  public static final String OK = "OK" + pad(49);
  public static final String ERROR = "ERROR" + pad(52);
  public static final String REVOKED = "REVOKED" + pad(52);
  public static final String INVALID_CUSTOMER_EMAIL = "invalidCustomerEmail" + pad(49);
  public static final String INVALID_CUSTOMER_NAME = "invalidCustomerName" + pad(49);

  /** Prefix the legacy client uses to recognize both "invalid customer" replies. */
  public static final String INVALID_CUSTOMER_PREFIX = "invalidCustomer";

  /** Content type Tomcat produced for the legacy text responses. */
  public static final String TEXT_CONTENT_TYPE = "text/html;charset=ISO-8859-1";

  public static final String BINARY_CONTENT_TYPE = "application/octet-stream";

  private LegacyResponses() {}

  /**
   * Legacy {@code AbstractServerCommand.processResponse}: {@code URLEncoder.encode(text, UTF-8)}
   * followed by the Linux line separator written by {@code PrintWriter.println}.
   */
  public static String encodeText(String text) {
    return URLEncoder.encode(text == null ? "null" : text, StandardCharsets.UTF_8) + "\n";
  }

  /**
   * Client-side inverse: strip line breaks and URL-decode, as legacy {@code RemoteUtils} did. A
   * body that is not URL-encoded (e.g. an HTML page from a server that does not know the command)
   * is returned as-is instead of throwing.
   */
  public static String decodeText(String body) {
    if (body == null) {
      return "";
    }
    String joined = body.replace("\r", "").replace("\n", "");
    try {
      return URLDecoder.decode(joined, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return joined;
    }
  }

  public static String urlEncode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  private static String pad(int n) {
    return "a".repeat(n);
  }
}
