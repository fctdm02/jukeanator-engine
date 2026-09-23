package com.djt.jukeanator_engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Resets the test {@code app.data-dir} (see application-test.yml) to its ground state once per
 * test run, before any test class -- and therefore before any Spring context -- is started.
 *
 * <p>Everything the engine writes there (JukeANator_*.json, *.oos, locations/, activity/, images/)
 * is transient and gitignored, so a file left over from an earlier run can drift out of step with
 * the current DTO shape (e.g. a renamed JSON field) and fail context startup on deserialization.
 * Only the checked-in fixtures in {@link #PRESERVED_FILE_NAMES} are kept; every subdirectory is
 * recreated on demand by the engine.
 *
 * <p>This is deliberately a JUnit Platform {@link LauncherSessionListener} rather than a
 * {@code @BeforeAll}: Spring caches application contexts across test classes, so a per-class wipe
 * would delete files out from under a context that is still in use, and it would not cover
 * {@code @SpringBootTest} classes that don't extend {@link AbstractServiceIntegrationTest}.
 * Registered via {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}.
 *
 * @author tmyers
 */
public class TestDataDirResetListener implements LauncherSessionListener {

  /** {@code app.data-dir} for tests (see application-test.yml). */
  private static final Path TEST_DATA_DIR =
      Path.of("src/test/resources/com/djt/jukeanator_engine/test-data-dir");

  /** Checked-in fixtures (see .gitignore) that must survive the reset. */
  private static final Set<String> PRESERVED_FILE_NAMES = Set.of("CDStats.TXT");

  @Override
  public void launcherSessionOpened(LauncherSession session) {

    if (!Files.isDirectory(TEST_DATA_DIR)) {
      return;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(TEST_DATA_DIR)) {
      for (Path entry : stream) {
        if (!PRESERVED_FILE_NAMES.contains(entry.getFileName().toString())) {
          deleteRecursively(entry);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not reset test data dir: " + TEST_DATA_DIR, e);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {

    if (!Files.isDirectory(path)) {
      Files.deleteIfExists(path);
      return;
    }

    // Deepest paths first, so each directory is empty by the time it is deleted.
    try (Stream<Path> walk = Files.walk(path)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
