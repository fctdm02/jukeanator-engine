package com.djt.jukeanator_engine.ui.components;

import java.awt.Component;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/**
 * Touch-screen helpers for clickable panels (rows, tiles, cards) that are not buttons.
 *
 * <p>
 * Clickable panels act on {@code mouseReleased} rather than {@code mouseClicked}. Single-touch
 * screens running in the driver's "Mouse Emulation" mode hold the button down while the finger is
 * on the glass, and the touch point jitters by a few pixels. AWT on Windows drops
 * {@code MOUSE_CLICKED} once the pointer moves past the system drag threshold (about 4 px), so
 * taps were silently lost. Acting on release whenever the finger lifts inside the component
 * matches how {@link javax.swing.JButton} behaves and works in both "Mouse Emulation" and
 * "Click on Touch" modes.
 */
public final class TouchInput {

  private TouchInput() {}

  /**
   * True when {@code e} is a left-button release that ended inside the component it was delivered
   * to, i.e. a completed tap. A press that is dragged off the component before release is ignored,
   * as it would be for a button.
   */
  public static boolean isTap(MouseEvent e) {
    Component source = e.getComponent();
    return SwingUtilities.isLeftMouseButton(e) && source != null && source.contains(e.getPoint());
  }
}
