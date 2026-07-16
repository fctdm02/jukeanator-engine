package com.djt.jukeanator_engine.domain.common.security;

import java.awt.EventQueue;
import java.awt.Toolkit;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Configures Spring Security for the LOCAL / Swing-UI operating mode.
 *
 * <p>
 * This bean is only active when {@code app.ui-enabled=true}. On startup it pushes a
 * {@link LocalAuthenticatedEventQueue} onto the AWT system event queue so that every event
 * dispatched on the EDT automatically has the LOCAL authentication installed in the
 * {@code SecurityContextHolder} ThreadLocal for the duration of that dispatch.
 *
 * <h3>Why not MODE_GLOBAL?</h3> {@code MODE_GLOBAL} stores one shared
 * {@link org.springframework.security.core.context.SecurityContext} for the entire JVM. JukeANator
 * runs an embedded HTTP server alongside the Swing UI; Spring Security's
 * {@code SecurityContextHolderFilter} calls {@code SecurityContextHolder.clearContext()} at the end
 * of every HTTP request. In {@code MODE_GLOBAL} that call destroys the shared context for ALL
 * threads at once (including the EDT), leaving the Swing UI unauthenticated until the next request
 * happens to re-populate it. The custom {@link EventQueue} approach avoids that race entirely: the
 * default {@code ThreadLocal} strategy remains in effect, and the EDT seeds its own ThreadLocal at
 * the start of each event dispatch.
 *
 * <h3>Background threads</h3> The {@code song-queue-thread} executor in
 * {@code SongPlayerServiceImpl} is not an AWT thread, so it is not covered by the event-queue
 * mechanism. Those tasks are wrapped with {@link SecurityContextPropagatingRunnable} (see the
 * {@code submitQueueProcessing()} diff), which seeds the background thread with the appropriate
 * auth before each queue-processing run.
 *
 * @author tmyers
 */
@Component
@ConditionalOnProperty(name = "app.ui-enabled", havingValue = "true")
public class LocalSecurityContextConfigurer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LocalSecurityContextConfigurer.class);

  @Override
  public void run(ApplicationArguments args) {

    /*
     * Push the authenticated event queue on the EDT.
     *
     * SwingUtilities.invokeLater() ensures this runs on the EDT itself, which is required by
     * EventQueue.push(). By the time ApplicationRunner.run() is called, the Swing UI has already
     * been initialised, so the EDT is live and accepting tasks.
     */
    SwingUtilities.invokeLater(() -> {
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(new LocalAuthenticatedEventQueue());

      log.info(
          "LocalAuthenticatedEventQueue installed — "
              + "all AWT-EventQueue dispatches will carry LOCAL authentication ("
              + "principal: {}, role: {})",
          LocalPrincipal.LOCAL_USERNAME, LocalPrincipal.LOCAL_ROLE);
    });
  }
}
