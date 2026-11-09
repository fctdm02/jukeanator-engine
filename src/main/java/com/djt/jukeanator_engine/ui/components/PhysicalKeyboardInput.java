package com.djt.jukeanator_engine.ui.components;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Feeds physical keyboard input to an overlay card whose fields are drawn labels (the on-screen
 * {@link KeyboardPanel} model) rather than focusable text fields. Used by the setup-time licensing
 * cards, where an attached keyboard is assumed.
 *
 * <p>While the owner is showing in the focused window (and {@code enabled} holds), every key event
 * is consumed, so none reaches the frame's WHEN_IN_FOCUSED_WINDOW bindings: Escape would otherwise
 * open the Admin panel over the card, and typing the bill-acceptor or card-reader key (e.g. the 'a'
 * in an email address) would add credits.
 *
 * <p>Keys: printable characters are typed; Backspace deletes; Tab / Shift+Tab and the Down / Up
 * arrows move between fields; Enter submits; Ctrl+V or Shift+Insert pastes.
 */
final class PhysicalKeyboardInput implements KeyEventDispatcher {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(PhysicalKeyboardInput.class);

  /** What the keystrokes do. */
  interface Target {
    void type(String text);

    void backspace();

    void enter();

    /** Moves to the next (+1) or previous (-1) field; single-field cards ignore it. */
    default void moveField(int delta) {}
  }

  private final JComponent owner;
  private final BooleanSupplier enabled;
  private final Target target;
  private boolean installed;

  PhysicalKeyboardInput(JComponent owner, BooleanSupplier enabled, Target target) {
    this.owner = owner;
    this.enabled = enabled;
    this.target = target;
  }

  /** Call from the owner's {@code addNotify()}. */
  void install() {
    if (!installed) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
      installed = true;
    }
  }

  /** Call from the owner's {@code removeNotify()} and when the card closes. */
  void uninstall() {
    if (installed) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
      installed = false;
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    if (!owner.isShowing() || !enabled.getAsBoolean()) {
      return false;
    }
    Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (focused == null || focused != SwingUtilities.getWindowAncestor(owner)) {
      return false; // e.g. the screen saver has focus; it handles its own keys
    }

    if (e.getID() == KeyEvent.KEY_PRESSED) {
      switch (e.getKeyCode()) {
        case KeyEvent.VK_BACK_SPACE -> target.backspace();
        case KeyEvent.VK_TAB -> target.moveField(e.isShiftDown() ? -1 : 1);
        case KeyEvent.VK_DOWN -> target.moveField(1);
        case KeyEvent.VK_UP -> target.moveField(-1);
        case KeyEvent.VK_ENTER -> target.enter();
        case KeyEvent.VK_V -> {
          if (e.isControlDown() && !e.isAltDown()) {
            paste();
          }
        }
        case KeyEvent.VK_INSERT -> {
          if (e.isShiftDown()) {
            paste();
          }
        }
        default -> {
          // Printable characters arrive as KEY_TYPED below; Escape and the rest do nothing here.
        }
      }
    } else if (e.getID() == KeyEvent.KEY_TYPED) {
      char c = e.getKeyChar();
      // Ctrl+<letter> arrives as a control character and is skipped; AltGr (Ctrl+Alt) characters
      // on international layouts are printable and accepted.
      if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c)) {
        target.type(String.valueOf(c));
      }
    }
    e.consume();
    return true;
  }

  private void paste() {
    try {
      Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
          .getData(DataFlavor.stringFlavor);
      if (data instanceof String text) {
        StringBuilder clean = new StringBuilder();
        text.trim().codePoints().filter(cp -> !Character.isISOControl(cp))
            .forEach(clean::appendCodePoint);
        target.type(clean.toString());
      }
    } catch (Exception ex) {
      log.debug("Clipboard paste failed", ex);
    }
  }
}
