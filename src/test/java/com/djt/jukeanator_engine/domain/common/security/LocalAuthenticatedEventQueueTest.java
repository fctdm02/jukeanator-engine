package com.djt.jukeanator_engine.domain.common.security;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.ActiveEvent;
import java.awt.AWTEvent;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Regression test for the reentrancy bug where a modal dialog's secondary event loop (a nested
 * {@code dispatchEvent} call on the same EDT thread) cleared the {@code SecurityContextHolder}
 * ThreadLocal out from under the outer dispatch, causing service calls made immediately after the
 * dialog closed (e.g. {@code AdminPanel.doFlushQueue()}) to be rejected as unauthenticated.
 *
 * <p>
 * {@link ActiveEvent} is used here (rather than a real {@code Component}) because
 * {@code EventQueue}'s default dispatch logic invokes {@code ActiveEvent.dispatch()} directly,
 * bypassing peer/listener plumbing that a headless test environment can't satisfy.
 */
class LocalAuthenticatedEventQueueTest {

  private static final int TEST_EVENT_ID = AWTEvent.RESERVED_ID_MAX + 1;

  @Test
  void nestedDispatchRestoresOuterContextInsteadOfClearingIt() {

    LocalAuthenticatedEventQueue queue = new LocalAuthenticatedEventQueue();
    final boolean[] authStillPresentAfterNestedDispatch = new boolean[1];

    AWTEvent nestedEvent = new FakeActiveEvent(() -> {
    });
    AWTEvent outerEvent = new FakeActiveEvent(() -> {
      // Simulate a modal JOptionPane/JFileChooser pumping a nested secondary loop through the
      // same queue instance before the outer handler resumes and calls a service method.
      queue.dispatchEvent(nestedEvent);

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      authStillPresentAfterNestedDispatch[0] = auth != null && auth.isAuthenticated();
    });

    queue.dispatchEvent(outerEvent);

    assertTrue(authStillPresentAfterNestedDispatch[0],
        "outer dispatch should still see LOCAL auth after a nested dispatch completes");
    assertNull(SecurityContextHolder.getContext().getAuthentication(),
        "context must not leak once the outermost dispatch has fully returned");
  }

  private static final class FakeActiveEvent extends AWTEvent implements ActiveEvent {
    private static final long serialVersionUID = 1L;
    private final transient Runnable action;

    FakeActiveEvent(Runnable action) {
      super(new Object(), TEST_EVENT_ID);
      this.action = action;
    }

    @Override
    public void dispatch() {
      action.run();
    }
  }
}
