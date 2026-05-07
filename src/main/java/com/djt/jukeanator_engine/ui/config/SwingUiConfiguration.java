package com.djt.jukeanator_engine.ui.config;

import static java.util.Objects.requireNonNull;

import javax.swing.SwingUtilities;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Configuration;

import com.djt.jukeanator_engine.ui.JukeANatorUserInterfaceApplication;

@Configuration
@EnableConfigurationProperties(UserInterfaceProperties.class)
@ConditionalOnProperty(
    prefix = "user-interface",
    name = "enabled",
    havingValue = "true")
public class SwingUiConfiguration {

  private final UserInterfaceProperties properties;

  public SwingUiConfiguration(UserInterfaceProperties properties) {
    requireNonNull(properties, "properties cannot be null");
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void launchUi() {

    SwingUtilities.invokeLater(() -> {

      new JukeANatorUserInterfaceApplication(
          properties.getBaseUrl());
    });
  }
}