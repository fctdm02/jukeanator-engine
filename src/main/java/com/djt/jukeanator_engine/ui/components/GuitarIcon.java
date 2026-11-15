package com.djt.jukeanator_engine.ui.components;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * A guitar silhouette painted with Java2D in the host component's foreground colour.
 *
 * <p>
 * Used for the GENRES tab instead of the U+1F3B8 emoji, which has no glyph in the fonts Java's
 * fallback chain picks on some Linux installs (e.g. Linux Mint with the distribution OpenJDK), where
 * it rendered as nothing at all. Painting the shape removes any dependency on installed fonts.
 */
final class GuitarIcon implements Icon {

  private final int size;
  private final Shape guitar;

  /**
   * @param size width and height of the icon in pixels (typically the tab icon font size, so it
   *        matches the font glyphs used by the other tabs)
   */
  GuitarIcon(int size) {
    this.size = Math.max(1, size);
    this.guitar = buildShape(this.size);
  }

  // Builds the guitar upright in a 100x100 design space, tilts it like the emoji, then scales and
  // centres it to fit a size x size box.
  private static Shape buildShape(int size) {

    Area body = new Area(new Ellipse2D.Double(27, 52, 46, 40)); // lower bout
    body.add(new Area(new Ellipse2D.Double(33, 34, 34, 30))); // upper bout
    body.subtract(new Area(new Ellipse2D.Double(44, 52, 12, 12))); // sound hole
    body.subtract(new Area(new Rectangle2D.Double(40, 74, 20, 4))); // bridge

    Area guitar = new Area(body);
    guitar.add(new Area(new Rectangle2D.Double(46, 10, 8, 30))); // neck
    guitar.add(new Area(new RoundRectangle2D.Double(42, 0, 16, 14, 5, 5))); // headstock
    for (int i = 0; i < 3; i++) { // tuning pegs
      double y = 2 + i * 4;
      guitar.add(new Area(new Rectangle2D.Double(38, y, 4, 2)));
      guitar.add(new Area(new Rectangle2D.Double(58, y, 4, 2)));
    }

    Shape tilted =
        AffineTransform.getRotateInstance(Math.toRadians(40), 50, 50).createTransformedShape(guitar);

    Rectangle2D bounds = tilted.getBounds2D();
    double scale = size / Math.max(bounds.getWidth(), bounds.getHeight());
    AffineTransform fit = new AffineTransform();
    fit.translate((size - bounds.getWidth() * scale) / 2.0,
        (size - bounds.getHeight() * scale) / 2.0);
    fit.scale(scale, scale);
    fit.translate(-bounds.getX(), -bounds.getY());
    return fit.createTransformedShape(tilted);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(c.getForeground());
      g2.translate(x, y);
      g2.fill(guitar);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public int getIconWidth() {
    return size;
  }

  @Override
  public int getIconHeight() {
    return size;
  }
}
