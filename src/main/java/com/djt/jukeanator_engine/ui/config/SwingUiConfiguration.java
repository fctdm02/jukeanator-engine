package com.djt.jukeanator_engine.ui.config;

import javax.swing.SwingUtilities;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.djt.jukeanator_engine.ui.JukeANatorUserInterfaceApplication;

@Configuration
@EnableConfigurationProperties(UserInterfaceProperties.class)
@ConditionalOnProperty(prefix = "user-interface", name = "enabled", havingValue = "true")
public class SwingUiConfiguration {

  private final JukeANatorUserInterfaceApplication uiApplication;

  public SwingUiConfiguration(JukeANatorUserInterfaceApplication uiApplication) {

    this.uiApplication = uiApplication;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void launchUi() {

    SwingUtilities.invokeLater(uiApplication::launch);
  }
}
