package com.djt.jukeanator_engine.ui.config;

import javax.swing.SwingUtilities;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import com.djt.jukeanator_engine.ui.JukeANatorUserInterfaceApplication;
import jakarta.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(prefix = "user-interface", name = "enabled", havingValue = "true")
public class SwingUiConfiguration {

  @PostConstruct
  public void launchUi() {

    SwingUtilities.invokeLater(() -> {

      new JukeANatorUserInterfaceApplication();
    });
  }
}
