package com.djt.jukeanator_engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

class ExternalConfigMergerTest {

  @TempDir
  private Path configDir;

  private static String merge(String bundled, String external, List<String> added) {
    return ExternalConfigMerger.mergeMissingProperties(bundled, external, added);
  }

  @Test
  void missingKey_isInsertedAfterItsPrecedingSibling_withItsComments() {

    String bundled = """
        app:
          mode: standalone
          # explains the flag
          new-flag: true   # trailing comment
          jwt:
            secret: bundled
        """;
    String external = """
        app:
          mode: slave
          jwt:
            secret: external
        """;
    String expected = """
        app:
          mode: slave
          # explains the flag
          new-flag: true   # trailing comment
          jwt:
            secret: external
        """;

    List<String> added = new ArrayList<>();
    assertEquals(expected, merge(bundled, external, added));
    assertEquals(List.of("app.new-flag"), added);
  }

  @Test
  void missingKey_withNoPrecedingSibling_isInsertedBeforeItsNextSibling() {

    String bundled = """
        app:
          first: 1
          mode: standalone
        """;
    String external = """
        app:
          mode: slave
        """;
    String expected = """
        app:
          first: 1
          mode: slave
        """;

    assertEquals(expected, merge(bundled, external, new ArrayList<>()));
  }

  @Test
  void missingSections_areInsertedWhole_andTopLevelOnesKeepBlankLineSeparation() {

    String bundled = """
        app:
          mode: standalone
          geo-fence:
            enabled: false
            # radius comment
            radius-meters: 50

        receipt:
          from-address: bundled

        financial-ledger:
          jukebox-split-percentage: 50   # split

        user-interface:
          always-on-top: false
        """;
    String external = """
        app:
          mode: slave

        receipt:
          from-address: external

        user-interface:
          always-on-top: true
        """;
    String expected = """
        app:
          mode: slave
          geo-fence:
            enabled: false
            # radius comment
            radius-meters: 50

        receipt:
          from-address: external

        financial-ledger:
          jukebox-split-percentage: 50   # split

        user-interface:
          always-on-top: true
        """;

    List<String> added = new ArrayList<>();
    assertEquals(expected, merge(bundled, external, added));
    assertEquals(List.of("app.geo-fence", "financial-ledger"), added);
  }

  @Test
  void commentedOutKey_isNotReAdded() {

    String bundled = """
        song-player:
          player-type: vlc
          winamp-exe-path: C:\\Winamp\\winamp.exe
        """;
    String external = """
        song-player:
          player-type: winamp
          # winamp-exe-path: C:\\Other\\winamp.exe
        """;

    List<String> added = new ArrayList<>();
    assertEquals(external, merge(bundled, external, added));
    assertTrue(added.isEmpty());
  }

  @Test
  void childrenAreNotAdded_underAParentThatHasAScalarValue() {

    String bundled = """
        app:
          geo-fence:
            enabled: false
        """;
    String external = """
        app:
          geo-fence: ~
        """;

    List<String> added = new ArrayList<>();
    assertEquals(external, merge(bundled, external, added));
    assertTrue(added.isEmpty());
  }

  @Test
  void externalLineSeparatorAndIndentation_arePreserved() {

    String bundled = "app:\n  mode: standalone\n  new-flag: true\n";
    String external = "app:\r\n    mode: slave\r\n";

    assertEquals("app:\r\n    mode: slave\r\n    new-flag: true\r\n",
        merge(bundled, external, new ArrayList<>()));
  }

  @Test
  void bundledApplicationYml_mergesIntoAMinimalFile_completelyAndIdempotently() throws Exception {

    String bundled = readBundled();
    String external = "app:\n  mode: slave\n";

    List<String> added = new ArrayList<>();
    String merged = merge(bundled, external, added);

    Map<String, Object> mergedMap = new Yaml().load(merged);
    assertEquals(flattenKeys(new Yaml().load(bundled)), flattenKeys(mergedMap));
    assertEquals("slave", ((Map<?, ?>) mergedMap.get("app")).get("mode"),
        "existing values are left alone");

    List<String> addedAgain = new ArrayList<>();
    assertEquals(merged, merge(bundled, merged, addedAgain));
    assertTrue(addedAgain.isEmpty(), "a second merge has nothing left to add");
  }

  @Test
  void addMissingProperties_writesTheFile_andKeepsABackup() throws Exception {

    Path configFile = configDir.resolve("application.yml");
    String original = "app:\n  mode: slave\n";
    Files.writeString(configFile, original, StandardCharsets.UTF_8);

    ExternalConfigMerger.addMissingProperties(configFile);

    assertTrue(Files.readString(configFile).contains("cycle-boundary-holdback:"));
    assertEquals(original, Files.readString(configDir.resolve("application.yml.bak")));
  }

  @Test
  void addMissingProperties_leavesACompleteFileUntouched() throws Exception {

    Path configFile = configDir.resolve("application.yml");
    String bundled = readBundled();
    Files.writeString(configFile, bundled, StandardCharsets.UTF_8);

    ExternalConfigMerger.addMissingProperties(configFile);

    assertEquals(bundled, Files.readString(configFile));
    assertFalse(Files.exists(configDir.resolve("application.yml.bak")));
  }

  private static String readBundled() throws Exception {

    try (var in = new ClassPathResource("application.yml").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Set<String> flattenKeys(Map<?, ?> map) {

    Set<String> keys = new TreeSet<>();
    flattenKeys("", map, keys);
    return keys;
  }

  private static void flattenKeys(String prefix, Map<?, ?> map, Set<String> keys) {

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = prefix + entry.getKey();
      keys.add(key);
      if (entry.getValue() instanceof Map<?, ?> child) {
        flattenKeys(key + ".", child, keys);
      }
    }
  }
}
