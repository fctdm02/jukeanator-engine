package com.djt.jukeanator_engine.domain.common.security;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.common.aop.ServiceSecurityAspect;

/**
 * A custom AWT {@link EventQueue} that installs the LOCAL authentication into the
 * {@code SecurityContextHolder} ThreadLocal before dispatching each Swing/AWT event, and clears it
 * in a {@code finally} block afterwards.
 *
 * <h3>Why this is needed</h3> Spring Security's default strategy is {@code MODE_THREADLOCAL}. The
 * AWT Event Dispatch Thread (EDT) is created by the JVM's AWT toolkit, completely outside the
 * Spring lifecycle, so it starts every event dispatch with an empty ThreadLocal security context.
 * Without this class, every Swing component that calls a service method (e.g. to refresh the queue
 * display) would be rejected by {@link ServiceSecurityAspect} with an
 * {@code InsufficientAuthenticationException}.
 *
 * <h3>Why NOT {@code MODE_GLOBAL}</h3> {@code MODE_GLOBAL} stores one shared
 * {@link SecurityContext} for the entire JVM. When JukeANator runs with an embedded HTTP server
 * alongside the Swing UI, Spring Security's {@code SecurityContextHolderFilter} calls
 * {@code SecurityContextHolder.clearContext()} at the end of every HTTP request. In
 * {@code MODE_GLOBAL} that single call destroys the shared context for all threads simultaneously —
 * including the EDT — leaving the Swing UI unauthenticated until the next HTTP request happens to
 * re-populate it. Using per-thread ThreadLocals (the default) and seeding the EDT via this class
 * avoids that race entirely.
 *
 * <h3>How it is installed</h3> {@link LocalSecurityContextConfigurer} pushes an instance of this
 * class onto the system {@link EventQueue} via
 * {@code Toolkit.getDefaultToolkit().getSystemEventQueue().push(...)}. Pushing replaces the active
 * queue: all subsequent event dispatches go through {@link #dispatchEvent(AWTEvent)} on this
 * subclass.
 *
 * <h3>Reentrancy</h3> Modal dialogs and blocking choosers ({@code JOptionPane.showConfirmDialog},
 * {@code JFileChooser.showOpenDialog}, etc.) pump a secondary event loop on the EDT that is
 * serviced by this same queue instance, so {@link #dispatchEvent(AWTEvent)} can be re-entered
 * before the outer call has returned. Because {@code SecurityContextHolder} is backed by a
 * {@code ThreadLocal}, an unconditional {@code clearContext()} in the inner call's {@code finally}
 * block would wipe out the context for the still-running outer dispatch as soon as the dialog
 * closes — e.g. a handler that shows a confirmation dialog and then calls a service method
 * afterwards would find itself unauthenticated. To avoid that, each call saves and restores the
 * context that was active before it ran, rather than clearing unconditionally.
 *
 * @author tmyers
 */
public final class LocalAuthenticatedEventQueue extends EventQueue {

  @Override
  protected void dispatchEvent(AWTEvent event) {

    /*
     * Save whatever context was active before this (possibly nested) dispatch, then install LOCAL
     * auth into this thread's (the EDT's) ThreadLocal security context for the duration of the
     * dispatch.
     */
    SecurityContext previous = SecurityContextHolder.getContext();

    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(LocalAuthenticationToken.INSTANCE);
    SecurityContextHolder.setContext(ctx);

    try {
      super.dispatchEvent(event);
    } finally {
      /*
       * Restore rather than clear, so a nested dispatch (e.g. from a modal dialog's secondary
       * event loop) hands control back to the outer dispatch with its context intact.
       */
      SecurityContextHolder.setContext(previous);
    }
  }
}
