package com.djt.jukeanator_engine.domain.license.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyResponsesTest {

  @Test
  void encodeThenDecode_roundTrips() {
    String text = "No more available licenses for given customer email: a@b.com";
    assertEquals(text, LegacyResponses.decodeText(LegacyResponses.encodeText(text)));
  }

  @Test
  void decode_paddedConstantUnchanged() {
    assertEquals(LegacyResponses.OK, LegacyResponses.decodeText(LegacyResponses.OK + "\r\n"));
  }

  @Test
  void decode_nonEncodedHtml_isReturnedAsIs() {
    // The legacy site answers unknown commands with its home page; "100%; " is not an escape.
    String html = "<html>\n<style>body { width: 100%; }</style>\n</html>";
    assertEquals("<html><style>body { width: 100%; }</style></html>",
        LegacyResponses.decodeText(html));
  }
}
