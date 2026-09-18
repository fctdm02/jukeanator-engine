package com.djt.jukeanator_engine.ui.components;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * A pill-rail / sliding-thumb toggle switch. The accentBlue border and white thumb are the same
 * in both states — only the rail's fill (blue gradient when on vs. dark idle when off, matching
 * {@code HomePanel#sortButton}'s Order By Artist/Album buttons) and the thumb's left/right
 * position signal on vs. off. There is no built-in Swing switch component, and no other
 * switch-style widget exists elsewhere in the codebase (only plain, unstyled
 * {@link javax.swing.JCheckBox} usages) — this is the first one, added for the Home tab's
 * "Legacy" view toggle.
 */
public class ToggleSwitch extends JButton {

  private static final long serialVersionUID = 1L;

  /** Notified whenever the user clicks the switch, after its internal state has flipped. */
  public interface ToggleListener {
    void onToggle(boolean isOn);
  }

  private boolean on;
  private ToggleListener listener;

  public ToggleSwitch(boolean initiallyOn) {
    this.on = initiallyOn;

    setPreferredSize(
        new Dimension(LayoutTheme.get().toggleSwitchW, LayoutTheme.get().toggleSwitchH));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    addActionListener(e -> {
      on = !on;
      repaint();
      if (listener != null) {
        listener.onToggle(on);
      }
    });

    addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseEntered(java.awt.event.MouseEvent e) {
        repaint();
      }

      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        repaint();
      }
    });
  }

  public boolean isOn() {
    return on;
  }

  /** Sets the switch's state without firing {@link ToggleListener}. */
  public void setOn(boolean on) {
    if (this.on != on) {
      this.on = on;
      repaint();
    }
  }

  public void setToggleListener(ToggleListener listener) {
    this.listener = listener;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int w = getWidth();
    int h = getHeight();
    int arc = h;

    // Rail — same fill/border as the Order By buttons: blue gradient + accentBlue border when
    // active, dark idle fill + neutral border when inactive.
    if (on) {
      g2.setPaint(new GradientPaint(0, 0, ColorTheme.get().navBtnGradTop, 0, h,
          ColorTheme.get().navBtnGradBottom));
    } else {
      g2.setColor(ColorTheme.get().sortBtnIdleBg);
    }
    g2.fillRoundRect(0, 0, w, h, arc, arc);

    // Border and thumb are the same color in both states — position (left/right) and the rail
    // fill are what actually communicate on/off.
    g2.setColor(ColorTheme.get().accentBlue);
    g2.setStroke(new java.awt.BasicStroke(1.5f));
    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

    // Sliding thumb
    int pad = 2;
    int thumbD = h - pad * 2;
    int thumbX = on ? w - thumbD - pad : pad;
    g2.setColor(ColorTheme.get().textPrimary);
    g2.fillOval(thumbX, pad, thumbD, thumbD);

    g2.dispose();
  }
}
