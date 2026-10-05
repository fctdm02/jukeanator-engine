package com.djt.jukeanator_engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ExternalConfigUpdaterTest {

  private static final String WINAMP_PATH = "C:\\kiosk\\Winamp\\winamp.exe";
  private static final String WINAMP_SCALAR = "'C:\\kiosk\\Winamp\\winamp.exe'";

  @Test
  void existingKeys_areReplacedAndCommentAndOtherBlocksKept() {

    String yaml = """
        app:
          mode: standalone
        song-player:
          player-type: vlc   # vlc or detached-vlc | winamp (windows only)
          winamp-exe-path: C:\\\\Program Files (x86)\\\\Winamp\\\\winamp.exe
          player-volume: 100

        location:
          player-type: untouched
        """;

    String expected = """
        app:
          mode: standalone
        song-player:
          player-type: winamp   # vlc or detached-vlc | winamp (windows only)
          winamp-exe-path: %s
          player-volume: 100

        location:
          player-type: untouched
        """.formatted(WINAMP_SCALAR);

    assertEquals(expected, ExternalConfigUpdater.setSongPlayerWinamp(yaml, WINAMP_PATH));
  }

  @Test
  void missingExePath_isInsertedIntoBlock() {

    String yaml = """
        song-player:
            player-type: vlc
            player-volume: 100
        """;

    String expected = """
        song-player:
            player-type: winamp
            winamp-exe-path: %s
            player-volume: 100
        """.formatted(WINAMP_SCALAR);

    assertEquals(expected, ExternalConfigUpdater.setSongPlayerWinamp(yaml, WINAMP_PATH));
  }

  @Test
  void missingBlock_isAppended() {

    String yaml = "app:\n  mode: standalone";

    String expected = """
        app:
          mode: standalone
        song-player:
          player-type: winamp
          winamp-exe-path: %s
        """.formatted(WINAMP_SCALAR);

    assertEquals(expected, ExternalConfigUpdater.setSongPlayerWinamp(yaml, WINAMP_PATH));
  }

  @Test
  void pathWithSpacesAndQuote_isSingleQuotedScalar() {

    String yaml = "song-player:\r\n  player-type: vlc\r\n";

    String expected = "song-player:\r\n"
        + "  player-type: winamp\r\n"
        + "  winamp-exe-path: 'C:\\Program Files (x86)\\Bob''s Winamp\\winamp.exe'\r\n";

    assertEquals(expected, ExternalConfigUpdater.setSongPlayerWinamp(yaml,
        "C:\\Program Files (x86)\\Bob's Winamp\\winamp.exe"));
  }

  @Test
  void resolveExternalConfigFile_usesAdditionalLocation() {

    MockEnvironment env = new MockEnvironment()
        .withProperty("spring.config.additional-location", "file:C:\\kiosk\\config/");

    assertEquals(Paths.get("C:\\kiosk\\config").resolve("application.yml"),
        ExternalConfigUpdater.resolveExternalConfigFile(env));
    assertNull(ExternalConfigUpdater.resolveExternalConfigFile(new MockEnvironment()));
  }
}
