package com.djt.jukeanator_engine.domain.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.common.aop.ServiceSecurityAspect;

/**
 * A {@link Runnable} decorator that captures the {@link SecurityContext} at construction time and
 * restores it on the executing thread before delegating to the wrapped {@code Runnable}, clearing
 * the context afterwards.
 *
 * <h3>Why this exists</h3> {@code SongPlayerServiceImpl} submits tasks to a single-thread
 * {@code ExecutorService} named {@code song-queue-thread}. When the application runs in REST/remote
 * mode, {@code SecurityContextHolder} uses the default {@code ThreadLocal} strategy, which means
 * the executor thread starts every task with an empty security context. Because
 * {@link ServiceSecurityAspect} requires an {@link Authentication} for every service call, all
 * queue-processing tasks must run with a valid authentication — typically
 * {@link SystemPrincipal.SystemAuthenticationToken}.
 *
 * <h3>Usage inside SongPlayerServiceImpl</h3>
 * 
 * <pre>{@code
 * private void submitQueueProcessing() {
 *   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *   Authentication effective =
 *       (auth != null) ? auth : SystemPrincipal.SystemAuthenticationToken.INSTANCE;
 *
 *   queueExecutor.submit(new SecurityContextPropagatingRunnable(() -> {
 *     try {
 *       processQueue();
 *     } catch (Exception e) {
 *       log.error("Queue processing failed", e);
 *     }
 *   }, effective));
 * }
 * }</pre>
 *
 * <p>
 * In LOCAL/Swing-UI mode this wrapper is a no-op in practice because
 * {@code SecurityContextHolder.MODE_GLOBAL} already makes the {@link LocalAuthenticationToken}
 * visible on every thread.
 *
 * @author tmyers
 */
public final class SecurityContextPropagatingRunnable implements Runnable {

  private final Runnable delegate;
  private final Authentication authentication;

  /**
   * Creates a propagating runnable that will run {@code delegate} with the supplied
   * {@code authentication} installed in the security context.
   *
   * @param delegate the actual work to execute
   * @param authentication the authentication to install; must not be {@code null}
   */
  public SecurityContextPropagatingRunnable(Runnable delegate, Authentication authentication) {
    if (delegate == null)
      throw new IllegalArgumentException("delegate must not be null");
    if (authentication == null)
      throw new IllegalArgumentException("authentication must not be null");
    this.delegate = delegate;
    this.authentication = authentication;
  }

  @Override
  public void run() {
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(authentication);
    SecurityContextHolder.setContext(ctx);
    try {
      delegate.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
