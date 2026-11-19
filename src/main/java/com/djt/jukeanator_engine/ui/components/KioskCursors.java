package com.djt.jukeanator_engine.ui.components;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

/**
 * Central source of the cursors the kiosk UI assigns to its components. When
 * {@code user-interface.hide-mouse-pointer} is enabled, {@link #hand()} and {@link #text()} return
 * a blank cursor, so no component ever shows a pointer, even for the moment between the pointer
 * arriving over it and any global listener reacting. Components must use these methods instead of
 * {@code Cursor.getPredefinedCursor(...)}.
 */
public final class KioskCursors {

  private static volatile boolean hidden;
  private static volatile Cursor blank;

  private KioskCursors() {}

  /** Enables hidden-pointer mode; call before the UI components are built. */
  public static void setHidden(boolean hide) {
    hidden = hide;
  }

  public static boolean isHidden() {
    return hidden;
  }

  /** A fully transparent cursor. */
  public static Cursor blank() {
    Cursor c = blank;
    if (c == null) {
      c = Toolkit.getDefaultToolkit().createCustomCursor(
          new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
      blank = c;
    }
    return c;
  }

  /** The hand cursor for clickable components, or the blank cursor when the pointer is hidden. */
  public static Cursor hand() {
    return hidden ? blank() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  /** The text cursor for editable fields, or the blank cursor when the pointer is hidden. */
  public static Cursor text() {
    return hidden ? blank() : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  }
}
