package com.djt.jukeanator_engine.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;

/**
 * Shared "pressed" motif for every custom-painted button: the face darkens slightly, an inset
 * shadow appears inside its top and side edges, and the face (or just its label, for buttons with
 * no outer shadow) shifts down a pixel or two. Label colours never change.
 *
 * <p>
 * Used by {@link ButtonFactory} (blue navigation buttons and the Admin Panel's overlay-card text
 * buttons), {@link Button3D} (3-D queue buttons), {@link Key3D} (keyboard keys and the Home letter
 * strip), the {@link AdminPanel} sidebar buttons and the {@link EditAlbumCard} buttons.
 */
final class PressedEffect {

  /** How much darker a pressed face is than its idle face (0.10 = 10%). */
  static final float DARKEN = 0.10f;

  /** Peak alpha of the inset shadow along the top edge. */
  private static final int INSET_TOP_ALPHA = 110;

  /** Peak alpha of the inset shadow along the left and right edges. */
  private static final int INSET_SIDE_ALPHA = 50;

  /** Alpha of the crisp 1px recessed line just inside the top edge. */
  private static final int INSET_EDGE_ALPHA = 90;

  private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

  private PressedEffect() {
  }

  /**
   * Returns true while the button is held down with the pointer still over it — the same state in
   * which releasing would fire the action.
   */
  static boolean isPressed(AbstractButton button) {
    ButtonModel model = button.getModel();
    return model.isArmed() && model.isPressed();
  }

  /** Returns {@code c} darkened by {@link #DARKEN}, keeping its alpha. */
  static Color darken(Color c) {
    float f = 1f - DARKEN;
    return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f),
        Math.round(c.getBlue() * f), c.getAlpha());
  }

  /** Returns a copy of the gradient stops, each darkened by {@link #DARKEN}. */
  static Color[] darken(Color[] stops) {
    Color[] out = new Color[stops.length];
    for (int i = 0; i < stops.length; i++) {
      out[i] = darken(stops[i]);
    }
    return out;
  }

  /**
   * Paints an inset (inner) shadow inside the given rounded rectangle: a soft band along the top
   * edge, fainter bands along the sides, and a crisp dark line just inside the top edge. Together
   * they make the face read as sitting below the surrounding surface.
   */
  static void paintInsetShadow(Graphics2D g2, int x, int y, int w, int h, int arc) {
    Shape oldClip = g2.getClip();
    g2.clip(new RoundRectangle2D.Float(x, y, w, h, arc, arc));

    int depth = Math.max(4, h / 6);
    int sideDepth = Math.max(2, depth / 2);

    // Top band
    g2.setPaint(new GradientPaint(0, y, new Color(0, 0, 0, INSET_TOP_ALPHA), 0, y + depth,
        TRANSPARENT));
    g2.fillRect(x, y, w, depth);

    // Left and right bands
    g2.setPaint(new GradientPaint(x, 0, new Color(0, 0, 0, INSET_SIDE_ALPHA), x + sideDepth, 0,
        TRANSPARENT));
    g2.fillRect(x, y, sideDepth, h);
    g2.setPaint(new GradientPaint(x + w, 0, new Color(0, 0, 0, INSET_SIDE_ALPHA),
        x + w - sideDepth, 0, TRANSPARENT));
    g2.fillRect(x + w - sideDepth, y, sideDepth, h);

    // Crisp recessed edge
    g2.setColor(new Color(0, 0, 0, INSET_EDGE_ALPHA));
    g2.setStroke(new BasicStroke(1f));
    g2.drawLine(x + arc / 2, y + 1, x + w - arc / 2 - 1, y + 1);

    g2.setClip(oldClip);
  }
}
