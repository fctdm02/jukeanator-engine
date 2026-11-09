package com.djt.jukeanator_engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExternalConfigUpdaterSectionTest {

  @Test
  void existingKeysReplaced_commentsKept_missingKeysInserted() {
    String yaml = """
        app:
          mode: standalone
        license:   # license block
          customer-email: ~   # registered email
          auto-provision-passcode: 'old'
        user-interface:
          num-credits: 0
        """;
    Map<String, String> values = new LinkedHashMap<>();
    values.put("customer-email", "a@b.com");
    values.put("auto-provision-passcode", null);
    values.put("location-name", "Joe's Bar");

    String expected = """
        app:
          mode: standalone
        license:   # license block
          location-name: 'Joe''s Bar'
          customer-email: 'a@b.com'   # registered email
          auto-provision-passcode: ~
        user-interface:
          num-credits: 0
        """;
    assertEquals(expected, ExternalConfigUpdater.setSectionValues(yaml, "license", values));
  }

  @Test
  void missingSection_isAppended() {
    String yaml = "app:\n  mode: slave\n";
    String result = ExternalConfigUpdater.setSectionValues(yaml, "license",
        Map.of("customer-email", "a@b.com"));
    assertEquals("app:\n  mode: slave\nlicense:\n  customer-email: 'a@b.com'\n", result);
  }
}
