package com.djt.jukeanator_engine.ui;

import javax.swing.JFrame;

public final class JukeANatorUserInterfaceApplication {

  public JukeANatorUserInterfaceApplication() {

    JukeANatorFrame frame = new JukeANatorFrame();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(400, 300);
    frame.setVisible(true);
  }
}
