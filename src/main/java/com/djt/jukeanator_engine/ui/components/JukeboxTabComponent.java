package com.djt.jukeanator_engine.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class JukeboxTabComponent extends JPanel {

  private static final long serialVersionUID = 1L;

  private final Color accentColor;

  public JukeboxTabComponent(String title, String iconText, Color accentColor) {

    this.accentColor = accentColor;
    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(8, 20, 8, 20));

    //
    // ICON
    //
    JLabel iconLabel = new JLabel(iconText);
    iconLabel.setAlignmentX(CENTER_ALIGNMENT);
    iconLabel.setForeground(accentColor);
    iconLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));

    //
    // TEXT
    //
    JLabel textLabel = new JLabel(title);
    textLabel.setAlignmentX(CENTER_ALIGNMENT);
    textLabel.setForeground(accentColor);
    textLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

    add(iconLabel);
    add(Box.createVerticalStrut(4));
    add(textLabel);
  }

  @Override
  protected void paintComponent(Graphics g) {

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    JTabbedPane pane = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
    if (pane != null) {

      int index = pane.indexOfTabComponent(this);
      boolean selected = pane.getSelectedIndex() == index;
      if (selected) {

        //
        // GLOW EFFECT
        //
        g2.setColor(
            new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 60));

        g2.fillRoundRect(6, 6, getWidth() - 12, getHeight() - 12, 18, 18);

        //
        // BORDER
        //
        g2.setColor(new Color(220, 220, 220));

        g2.drawRoundRect(6, 6, getWidth() - 13, getHeight() - 13, 18, 18);
      }
    }

    g2.dispose();

    super.paintComponent(g);
  }

  @Override
  public Dimension getPreferredSize() {

    return new Dimension(200, 92);
  }
}
