package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;

/**
 * Shared rendering for the small 3-D keys used by the {@link KeyboardPanel} keys and the Home screen
 * letter strip ({@link AlbumPager}).
 *
 * <p>
 * <b>Pressed state</b> (the shared {@link PressedEffect} motif): the key sinks toward its shadow,
 * the face darkens slightly, the specular highlight disappears and an inset shadow appears inside
 * the top and side edges. The label keeps its colour and sinks with the face.
 */
final class Key3D {

  /** Corner radius of a key. */
  private static final int ARC = 7;

  /** Bottom pixels reserved for the drop shadow when idle / when pressed. */
  private static final int SHADOW_H = 4;
  private static final int PRESSED_SHADOW_H = 1;

  /** How far a pressed key sinks into its housing. */
  private static final int PRESS_DROP = 2;

  private Key3D() {
  }

  /**
   * Paints a whole key — body and centred label ({@code button.getText()} in
   * {@code button.getFont()}) — onto {@code g}.
   *
   * @param g the graphics passed to {@code paintComponent}
   * @param button the key being painted
   * @param highlighted draw the accent "active" border around the key
   */
  static void paint(Graphics g, AbstractButton button, boolean highlighted) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    ColorTheme theme = ColorTheme.get();
    int w = button.getWidth();
    int h = button.getHeight();
    boolean pressed = PressedEffect.isPressed(button);

    // Reserve bottom pixels for the shadow; the visible key sits in [0, visH).
    // A pressed key sinks into its housing, so its drop-shadow shrinks.
    int shadowH = pressed ? PRESSED_SHADOW_H : SHADOW_H;
    int visH = h - shadowH;
    g2.translate(0, pressed ? PRESS_DROP : 0);

    // Drop-shadow slab
    g2.setColor(theme.keyShadow);
    g2.fillRoundRect(1, shadowH, w - 2, visH, ARC, ARC);

    // Shelf band (bottom ~25 % of the visible key)
    int shelfH = Math.round(visH * 0.25f);
    int faceH = visH - shelfH;
    g2.setColor(theme.keyShelf);
    g2.fillRoundRect(1, faceH, w - 2, shelfH + ARC / 2, ARC, ARC);

    // Face gradient — darkened slightly while pressed
    Color[] face = {theme.keyFaceTop, theme.keyFaceMid, theme.keyFaceBottom};
    if (pressed) {
      face = PressedEffect.darken(face);
    }
    g2.setPaint(new LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.55f, 1f}, face));
    g2.fillRoundRect(1, 0, w - 2, faceH + ARC / 2, ARC, ARC);

    // Specular top-edge highlight — a sunken key doesn't catch the light
    if (!pressed) {
      g2.setColor(theme.keyHighlight);
      g2.setStroke(new BasicStroke(1.2f));
      g2.drawLine(ARC, 1, w - ARC - 1, 1);
    }

    // Side-edge sheens
    g2.setColor(theme.keySide);
    g2.setStroke(new BasicStroke(1f));
    g2.drawLine(1, 2, 1, faceH - 2);
    g2.drawLine(w - 2, 2, w - 2, faceH - 2);

    // Inset shadow — the pressed key now sits below the surrounding surface
    if (pressed) {
      PressedEffect.paintInsetShadow(g2, 1, 0, w - 2, visH, ARC);
    }

    // Label — vertically centred in faceH, same colour pressed or not
    String text = button.getText();
    g2.setFont(button.getFont());
    FontMetrics fm = g2.getFontMetrics();
    g2.setColor(theme.textPrimary);
    g2.drawString(text, (w - fm.stringWidth(text)) / 2,
        (faceH - fm.getHeight()) / 2 + fm.getAscent());

    // Active-mode accent border around the key
    if (highlighted) {
      g2.setColor(theme.accentBlue);
      g2.setStroke(new BasicStroke(2.0f));
      g2.drawRoundRect(1, 1, w - 3, h - 3, ARC, ARC);
    }

    g2.dispose();
  }
}
