package com.djt.jukeanator_engine.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user-interface")
public class UserInterfaceProperties {

  private boolean enabled = false; // if true, a JFC/Swing UI is launched, otherwise, a headless backend
  private String baseUrl = "http://localhost:8080";

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
