package com.djt.jukeanator_engine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;

/**
 * Abstract base class for {@code @SpringBootTest} integration tests that exercise
 * service methods directly.
 *
 * <h3>Security context</h3>
 * {@link ServiceSecurityAspect} requires an authenticated principal in the
 * {@code SecurityContextHolder} before any service call.  JUnit test threads carry no
 * such context by default (there is no HTTP request, no EDT, and no
 * {@code LocalSecurityContextConfigurer}).  {@link #setUpSecurityContext()} installs
 * {@link SystemPrincipal.SystemAuthenticationToken} before each test method, and
 * {@link #clearSecurityContext()} removes it afterwards to prevent leakage between
 * tests.
 *
 * <h3>Test fixture cleanup</h3>
 * {@link #cleanup()} deletes the serialised song-library object file(s) written by the
 * scanner/repository under {@code src/test/resources}.  The persisted filename is derived from
 * the current location name (see {@code SongLibraryRepositoryFileSystemImpl}), so this sweeps
 * every {@code *.oos} file in the data dir rather than a single fixed name.  It runs before and
 * after the entire test class so each run starts and finishes with a clean slate.
 *
 * @author tmyers
 */
public abstract class AbstractServiceIntegrationTest {

  /** {@code app.data-dir} for tests (see application-test.yml); entirely transient/gitignored. */
  private static final String TEST_DATA_DIR =
      "src/test/resources/com/djt/jukeanator_engine/test-data-dir";

  // ── Security context lifecycle ────────────────────────────────────────────

  /**
   * Installs a {@link SystemPrincipal.SystemAuthenticationToken} into this thread's
   * {@code SecurityContextHolder} so that {@code ServiceSecurityAspect} allows every
   * service call made during the test.
   */
  @BeforeEach
  void setUpSecurityContext() {
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(SystemPrincipal.SystemAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(ctx);
  }

  /** Clears the {@code ThreadLocal} security context after each test. */
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ── Test-fixture cleanup ──────────────────────────────────────────────────

  @BeforeAll
  public static void beforeAll() throws IOException {
    cleanup();
  }

  @AfterAll
  public static void afterAll() throws IOException {
    cleanup();
  }

  /**
   * Deletes every serialised object-store ({@code *.oos}) file produced by the song scanner /
   * repository so that each test run starts from a known empty state.
   */
  public static void cleanup() throws IOException {

    Path dataDir = Path.of(TEST_DATA_DIR);
    if (!Files.isDirectory(dataDir)) {
      return;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.oos")) {
      for (Path oosFile : stream) {
        Files.deleteIfExists(oosFile);
      }
    }
  }
}