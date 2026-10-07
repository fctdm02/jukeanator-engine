package com.djt.jukeanator_engine.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Helpers for driving a live JFC/Swing UI from a JUnit test thread, without AssertJ Swing or
 * {@link java.awt.Robot}.
 *
 * <p>
 * Every read or write of a Swing component goes through the Event Dispatch Thread ({@link #onEdt},
 * {@link #runOnEdt}), so the test never touches the component tree concurrently with the UI.
 * Components are located by the names in
 * {@link com.djt.jukeanator_engine.ui.components.UiComponentNames}; repeated components (rows,
 * tiles) are told apart by the text of a descendant {@link JLabel}.
 *
 * <p>
 * Interactions mirror what a tap does: buttons are pressed with {@link AbstractButton#doClick()},
 * clickable panels have their {@code mouseClicked} listeners fired, and keyboard shortcuts invoke
 * the {@link Action} bound to the key stroke in the component's {@code WHEN_IN_FOCUSED_WINDOW}
 * input map, so the real binding for the configured key is exercised.
 */
public final class SwingTestSupport {

  /** Default time to wait for asynchronous service calls and UI updates to land. */
  public static final long DEFAULT_TIMEOUT_MS = 15_000;

  private SwingTestSupport() {}

  // ── EDT access ──────────────────────────────────────────────────────────────

  /**
   * Runs {@code work} on the EDT and returns its result, rethrowing anything it throws. Reentrant:
   * when already on the EDT, {@code work} simply runs in place.
   */
  public static <T> T onEdt(Callable<T> work) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return work.call();
    }
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Exception> failure = new AtomicReference<>();
    AtomicReference<Error> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        result.set(work.call());
      } catch (Exception e) {
        failure.set(e);
      } catch (Error e) {
        error.set(e);
      }
    });
    if (error.get() != null) {
      throw error.get();
    }
    if (failure.get() != null) {
      throw failure.get();
    }
    return result.get();
  }

  /** Runs {@code work} on the EDT and waits for it to finish. */
  public static void runOnEdt(Runnable work) throws Exception {
    onEdt(() -> {
      work.run();
      return null;
    });
  }

  /**
   * Waits until everything already queued on the EDT has run. Called twice, because a lot of UI
   * work (setQueue, credit listeners, dismissals) posts a second {@code invokeLater} from inside
   * the first.
   */
  public static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return; // queued work cannot run until the current EDT task returns
    }
    SwingUtilities.invokeAndWait(() -> {
    });
    SwingUtilities.invokeAndWait(() -> {
    });
  }

  // ── Waiting ─────────────────────────────────────────────────────────────────

  /** Polls {@code condition} until it is true, failing with {@code description} on timeout. */
  public static void await(Callable<Boolean> condition, String description) throws Exception {
    await(condition, description, DEFAULT_TIMEOUT_MS);
  }

  public static void await(Callable<Boolean> condition, String description, long timeoutMs)
      throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (true) {
      if (Boolean.TRUE.equals(condition.call())) {
        flushEdt();
        return;
      }
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Timed out after " + timeoutMs + " ms waiting for: " + description);
      }
      Thread.sleep(100);
    }
  }

  /** Polls until {@code actual} equals {@code expected}, then asserts it (for a clear failure). */
  public static <T> void awaitEquals(T expected, Callable<T> actual, String description)
      throws Exception {
    long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
    while (!expected.equals(actual.call()) && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    flushEdt();
    assertEquals(expected, actual.call(), description);
  }

  // ── Finding components ──────────────────────────────────────────────────────

  /** Every descendant of {@code root} of the given type, in tree (on-screen) order. */
  public static <T extends Component> List<T> descendants(Container root, Class<T> type) {
    List<T> found = new ArrayList<>();
    for (Component child : root.getComponents()) {
      if (type.isInstance(child)) {
        found.add(type.cast(child));
      }
      if (child instanceof Container container) {
        found.addAll(descendants(container, type));
      }
    }
    return found;
  }

  /** Every descendant of {@code root} with the given name and type. Call on the EDT. */
  public static <T extends Component> List<T> findAll(Container root, String name, Class<T> type) {
    return descendants(root, type).stream().filter(c -> name.equals(c.getName())).toList();
  }

  /** Every showing descendant of {@code root} with the given name and type. Call on the EDT. */
  public static <T extends Component> List<T> findAllShowing(Container root, String name,
      Class<T> type) {
    return findAll(root, name, type).stream().filter(Component::isShowing).toList();
  }

  /** The single showing descendant with the given name, or empty. Call on the EDT. */
  public static <T extends Component> Optional<T> findShowing(Container root, String name,
      Class<T> type) {
    return findAllShowing(root, name, type).stream().findFirst();
  }

  /** As {@link #findShowing}, but fails when no such component is showing. Call on the EDT. */
  public static <T extends Component> T requireShowing(Container root, String name,
      Class<T> type) {
    return findShowing(root, name, type)
        .orElseThrow(() -> new AssertionError("No showing component named " + name));
  }

  /** The first descendant with the given name, showing or not, or empty. Call on the EDT. */
  public static <T extends Component> Optional<T> find(Container root, String name, Class<T> type) {
    return findAll(root, name, type).stream().findFirst();
  }

  /**
   * The first showing component named {@code name} that contains a label whose text contains
   * {@code text} (album and song labels may be HTML-wrapped). Call on the EDT.
   */
  public static <T extends Container> Optional<T> findShowingWithText(Container root, String name,
      Class<T> type, String text) {
    return findAllShowing(root, name, type).stream().filter(c -> containsLabelText(c, text))
        .findFirst();
  }

  /** True when {@code root} or one of its descendants is a label whose text contains {@code text}. */
  public static boolean containsLabelText(Container root, String text) {
    if (root instanceof JLabel label && label.getText() != null && label.getText().contains(text)) {
      return true;
    }
    return descendants(root, JLabel.class).stream()
        .anyMatch(l -> l.getText() != null && l.getText().contains(text));
  }

  /** The texts of every label under {@code root}, in tree order. Call on the EDT. */
  public static List<String> labelTexts(Container root) {
    return descendants(root, JLabel.class).stream().map(JLabel::getText)
        .filter(t -> t != null && !t.isBlank()).toList();
  }

  /** True when a showing component named {@code cardName} exists under {@code root}. */
  public static boolean isCardShowing(Container root, String cardName) throws Exception {
    return onEdt(() -> !findAllShowing(root, cardName, Component.class).isEmpty());
  }

  /** The first visible top-level window matching {@code predicate}, or empty. */
  public static Optional<Window> findWindow(Predicate<Window> predicate) {
    for (Window window : Window.getWindows()) {
      if (window.isShowing() && predicate.test(window)) {
        return Optional.of(window);
      }
    }
    return Optional.empty();
  }

  // ── Interacting ─────────────────────────────────────────────────────────────

  /** Presses the button on the EDT, as a tap would (a disabled button ignores it). */
  public static void click(AbstractButton button) throws Exception {
    runOnEdt(button::doClick);
    flushEdt();
  }

  /** Finds the showing button named {@code name} under {@code root} and presses it. */
  public static void click(Container root, String name) throws Exception {
    click(onEdt(() -> requireShowing(root, name, AbstractButton.class)));
  }

  /**
   * Fires {@code mouseClicked} on {@code component}, or on its nearest ancestor that listens for
   * mouse events, as a tap on a clickable panel (row, tile, credits panel) would.
   */
  public static void clickComponent(Component component) throws Exception {
    runOnEdt(() -> {
      Component target = component;
      while (target != null && target.getMouseListeners().length == 0) {
        target = target.getParent();
      }
      if (target == null) {
        throw new AssertionError("No clickable component at or above " + component);
      }
      MouseEvent click = new MouseEvent(target, MouseEvent.MOUSE_CLICKED,
          System.currentTimeMillis(), 0, 5, 5, 1, false, MouseEvent.BUTTON1);
      for (MouseListener listener : target.getMouseListeners()) {
        listener.mouseClicked(click);
      }
    });
    flushEdt();
  }

  /**
   * Invokes the action bound to {@code stroke} in {@code component}'s
   * {@code WHEN_IN_FOCUSED_WINDOW} input map, as pressing the key (or a hardware device that
   * sends it, such as the bill acceptor) would.
   */
  public static void pressKeyBinding(JComponent component, KeyStroke stroke) throws Exception {
    runOnEdt(() -> {
      Object actionKey = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
      if (actionKey == null) {
        throw new AssertionError("No key binding for " + stroke + " on " + component.getName());
      }
      Action action = component.getActionMap().get(actionKey);
      if (action == null) {
        throw new AssertionError("No action named " + actionKey + " for " + stroke);
      }
      action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED,
          String.valueOf(actionKey)));
    });
    flushEdt();
  }
}
