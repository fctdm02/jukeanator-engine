package com.djt.jukeanator_engine.domain.license.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.djt.jukeanator_engine.domain.license.config.LicenseProperties;

class LicenseServerClientTest {

  @Test
  void legacyBuildDate_fitsLegacyColumn() {
    // The legacy server stores buildDate in CUSTOMER_SERIAL.BUILD_DATE VARCHAR(10).
    assertTrue(LicenseServerClient.legacyBuildDate().matches("\\d{2}-\\d{2}-\\d{4}"));
  }

  @Test
  void provisionUri_doubleEncodesAndSendsLegacyBuildDate() {
    LicenseProperties properties = new LicenseProperties();
    properties.setServerUrl("https://www.jukeanator.com/");
    LicenseServerClient client = new LicenseServerClient(properties, "1.0.0-SNAPSHOT", "standalone");

    Map<String, String> params = new LinkedHashMap<>();
    params.put("customerEmail", "owner@example.com");
    params.put("buildDate", LicenseServerClient.legacyBuildDate());
    URI uri = client.buildUri("provision", params);

    String s = uri.toString();
    assertTrue(s.startsWith("https://www.jukeanator.com/FrontController?command=provision&"), s);
    assertTrue(s.contains("customerEmail=owner%2540example.com"), s);
    assertTrue(s.matches(".*buildDate=\\d{2}-\\d{2}-\\d{4}.*"), s);
  }
}
