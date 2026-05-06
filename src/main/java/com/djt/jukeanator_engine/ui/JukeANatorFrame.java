package com.djt.jukeanator_engine.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

public class JukeANatorFrame extends JFrame {

  private static final long serialVersionUID = -5819744874210041265L;

  public JukeANatorFrame() {
    initialize();
  }

  private void initialize() {
    setTitle("JukeANator");
    setSize(800, 600);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    JLabel label = new JLabel("JukeANator", SwingConstants.CENTER);
    label.setFont(new Font("Arial", Font.BOLD, 24));

    add(label, BorderLayout.CENTER);
  }
}
