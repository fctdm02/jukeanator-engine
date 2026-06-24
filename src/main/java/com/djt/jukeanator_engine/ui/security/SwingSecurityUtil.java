package com.djt.jukeanator_engine.ui.security;

import java.util.concurrent.CompletableFuture;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;

/**
 * Utility for Swing components that need to call service methods from a background thread launched
 * via {@link CompletableFuture#runAsync(Runnable)}.
 *
 * <h3>The problem</h3> {@code CompletableFuture.runAsync()} without an explicit executor submits
 * work to the {@code ForkJoinPool.commonPool}. Those pool threads are not AWT threads, so the
 * {@code LocalAuthenticatedEventQueue} does not seed them with a security context.
 * {@link com.djt.jukeanator_engine.domain.common.aop.ServiceSecurityAspect} will then reject any
 * service call made on them.
 *
 * <h3>The solution</h3> {@link #runAsync(Runnable)} captures the {@link Authentication} that is
 * currently present on the calling thread (typically the EDT, already seeded by
 * {@code LocalAuthenticatedEventQueue}) and installs it into the ForkJoin thread's
 * {@code ThreadLocal} security context for the duration of the task. If the calling thread has no
 * authentication for any reason, it falls back to the internal
 * {@link SystemPrincipal.SystemAuthenticationToken}.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * SwingSecurityUtil.runAsync(() -> songQueueService.removeSong(req));
 * }</pre>
 *
 * @author tmyers
 */
public final class SwingSecurityUtil {

  private SwingSecurityUtil() {}

  /**
   * Equivalent to {@link CompletableFuture#runAsync(Runnable)} but propagates the current thread's
   * {@link Authentication} into the async task.
   *
   * @param task the work to run asynchronously; must not be {@code null}
   * @return the {@link CompletableFuture} representing the async computation
   */
  public static CompletableFuture<Void> runAsync(Runnable task) {
    Authentication effective = captureAuth();
    return CompletableFuture.runAsync(new SecurityContextPropagatingRunnable(task, effective));
  }

  // ── Internal ─────────────────────────────────────────────────────────────

  /**
   * Returns the authentication on the calling thread, or
   * {@link SystemPrincipal.SystemAuthenticationToken#INSTANCE} as a safe fallback.
   */
  private static Authentication captureAuth() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.isAuthenticated()) ? auth
        : SystemPrincipal.SystemAuthenticationToken.INSTANCE;
  }
}
