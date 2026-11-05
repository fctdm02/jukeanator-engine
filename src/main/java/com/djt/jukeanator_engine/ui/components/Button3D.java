package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;

/**
 * Shared rendering for the deep-blue 3-D extruded buttons used by {@link AddSongToQueueCard} and
 * {@link QueuePanel}.
 *
 * <p>
 * Each button paints its own label, but the body (drop shadow, shelf, face gradient, specular
 * highlight, side sheen and border) comes from {@link #paintBody}, so every 3-D button has the same
 * idle, hover, pressed and warning (red / not available) looks.
 *
 * <p>
 * <b>Pressed state</b> (the shared {@link PressedEffect} motif): the body sinks onto its drop
 * shadow, the face and border darken slightly, the specular highlight disappears and an inset
 * shadow appears inside the top and side edges. Label colours do not change; the label sinks with
 * the face.
 */
final class Button3D {

  /** Text shown on the second label line when a button is disabled for a non-credit reason. */
  static final String NOT_AVAILABLE_TEXT = "NOT AVAILABLE";

  /** Corner radius of the button body. */
  static final int ARC = 12;

  /** Bottom pixels reserved for the drop shadow; the visible button lives within [0, visH). */
  private static final int SHADOW_H = 5;

  /** How far a pressed button sinks toward its shadow (most of {@link #SHADOW_H}). */
  private static final int PRESS_DROP = 3;

  private static final String HOVER_PROPERTY = "jukeanator.button3dHovered";

  /** Geometry of a painted body, for laying out the label. */
  record Face(int width, int visH, int faceH) {
  }

  private Button3D() {
  }

  /**
   * Applies the common 3-D button setup: transparent content area, no LAF border or focus ring, hand
   * cursor, the given preferred size, and hover tracking (enabled buttons only).
   */
  static void configure(AbstractButton button, Dimension preferredSize) {
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setPreferredSize(preferredSize);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (button.isEnabled()) {
          button.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
          button.repaint();
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        button.putClientProperty(HOVER_PROPERTY, null);
        button.repaint();
      }
    });
  }

  /** Creates an anti-aliased (shapes and text) copy of {@code g}; the caller disposes it. */
  static Graphics2D createGraphics(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    return g2;
  }

  /** Draws {@code text} horizontally centred within {@code width} at baseline {@code y}. */
  static void drawCentered(Graphics2D g2, String text, int width, int y) {
    FontMetrics fm = g2.getFontMetrics();
    g2.drawString(text, (width - fm.stringWidth(text)) / 2, y);
  }

  /**
   * Paints the button body. When the button is pressed, {@code g2} is left translated down by the
   * press drop, so a label drawn afterwards with the returned geometry sinks with the face.
   *
   * @param g2 graphics to paint with (see {@link #createGraphics})
   * @param button the button being painted
   * @param warn {@code true} for the red warning / not-available look
   * @param accentColor border color in the idle state
   * @return the body geometry for laying out the label
   */
  static Face paintBody(Graphics2D g2, AbstractButton button, boolean warn, Color accentColor) {

    ColorTheme theme = ColorTheme.get();
    boolean pressed = !warn && PressedEffect.isPressed(button);
    boolean hovered = !warn && !pressed && isHovered(button);

    int w = button.getWidth();
    int visH = button.getHeight() - SHADOW_H;
    int shelfH = Math.round(visH * 0.22f);
    int faceH = visH - shelfH;

    // A pressed button sinks toward its shadow; the shadow's bottom edge stays put.
    int drop = pressed ? PRESS_DROP : 0;
    g2.translate(0, drop);

    // 1. Drop-shadow slab
    g2.setColor(theme.btn3dShadow);
    g2.fillRoundRect(2, SHADOW_H - drop, w - 4, visH, ARC, ARC);

    // 2. Shelf band (bottom ~22 % of the visible body)
    g2.setColor(warn ? theme.btn3dWarnShelf : theme.btn3dShelf);
    g2.fillRoundRect(1, faceH, w - 2, shelfH + ARC / 2, ARC, ARC);

    // 3. Face gradient — extends slightly into the shelf so the join is seamless
    Color[] face;
    if (warn) {
      face = new Color[] {theme.btn3dWarnTop, theme.btn3dWarnMid, theme.btn3dWarnBottom};
    } else if (pressed) {
      face = PressedEffect.darken(idleFace(theme));
    } else if (hovered) {
      face = new Color[] {theme.btn3dHoverTop, theme.btn3dHoverMid, theme.btn3dHoverBottom};
    } else {
      face = idleFace(theme);
    }
    g2.setPaint(new LinearGradientPaint(0, 0, 0, faceH, new float[] {0f, 0.5f, 1f}, face));
    g2.fillRoundRect(1, 0, w - 2, faceH + ARC / 2, ARC, ARC);

    // 4. Specular top-edge highlight — a sunken button doesn't catch the light
    if (!pressed) {
      g2.setColor(warn ? theme.btn3dWarnSpecular : theme.btn3dHighlight);
      g2.setStroke(new BasicStroke(1.2f));
      g2.drawLine(ARC, 1, w - ARC - 1, 1);
    }

    // 5. Side-edge sheen
    g2.setColor(warn ? theme.btn3dWarnSide : theme.btn3dSide);
    g2.setStroke(new BasicStroke(1f));
    g2.drawLine(1, 3, 1, faceH - 3);
    g2.drawLine(w - 2, 3, w - 2, faceH - 3);

    // 6. Inset shadow — the pressed body now sits below the surrounding surface
    if (pressed) {
      PressedEffect.paintInsetShadow(g2, 1, 0, w - 2, visH, ARC);
    }

    // 7. Border
    if (warn) {
      g2.setColor(theme.btn3dWarnBorder);
    } else if (pressed) {
      g2.setColor(PressedEffect.darken(accentColor));
    } else {
      g2.setColor(hovered ? accentColor.brighter() : accentColor);
    }
    g2.setStroke(new BasicStroke(2f));
    g2.drawRoundRect(1, 1, w - 3, visH - 2, ARC, ARC);

    return new Face(w, visH, faceH);
  }

  private static Color[] idleFace(ColorTheme theme) {
    return new Color[] {theme.btn3dFaceTop, theme.btn3dFaceMid, theme.btn3dFaceBottom};
  }

  private static boolean isHovered(AbstractButton button) {
    return button.isEnabled() && Boolean.TRUE.equals(button.getClientProperty(HOVER_PROPERTY));
  }
}
