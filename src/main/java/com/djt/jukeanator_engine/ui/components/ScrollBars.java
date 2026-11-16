package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

/**
 * Single place that decides which scroll bar every JFC/Swing scroll pane uses, so the bars look
 * the same throughout the app.
 *
 * <p>
 * Controlled by {@code user-interface.use-big-scrollbars} (default {@code true}): when on, bars
 * get the wide, touch-friendly {@link TouchScrollBarUI}; when off, they keep the look and feel's
 * normal mouse-sized bar. {@link JukeANatorFrame} sets the flag from config before it builds any
 * panel.
 */
public final class ScrollBars {

  private static volatile boolean useBigScrollBars = true;

  private ScrollBars() {
  }

  /** Sets whether scroll panes built from now on use the big, touch-friendly scroll bars. */
  public static void setUseBigScrollBars(boolean useBig) {
    useBigScrollBars = useBig;
  }

  /** @return whether the big, touch-friendly scroll bars are in use */
  public static boolean isUseBigScrollBars() {
    return useBigScrollBars;
  }

  /**
   * Applies the configured scroll bar to both of {@code scrollPane}'s bars. Does nothing when big
   * scroll bars are off, leaving the look and feel's default.
   */
  public static void apply(JScrollPane scrollPane) {
    apply(scrollPane.getVerticalScrollBar());
    apply(scrollPane.getHorizontalScrollBar());
  }

  /**
   * Applies the configured scroll bar to both of {@code scrollPane}'s bars, framed in
   * {@code frameColor} (normally the border color of the panel hosting the scroll pane). Does
   * nothing when big scroll bars are off.
   */
  public static void apply(JScrollPane scrollPane, Color frameColor) {
    apply(scrollPane.getVerticalScrollBar(), frameColor);
    apply(scrollPane.getHorizontalScrollBar(), frameColor);
  }

  /**
   * Applies the configured scroll bar to {@code bar}, framed in the default frame color (the
   * legacy CD card's white border). Does nothing when big scroll bars are off, leaving the look
   * and feel's default.
   */
  public static void apply(JScrollBar bar) {
    apply(bar, ColorTheme.get().legacyPanelBorder);
  }

  /**
   * Applies the configured scroll bar to {@code bar}, framed in {@code frameColor}. Does nothing
   * when big scroll bars are off, leaving the look and feel's default.
   */
  public static void apply(JScrollBar bar, Color frameColor) {
    if (useBigScrollBars) {
      bar.setUI(new TouchScrollBarUI(frameColor));
    }
  }
}
