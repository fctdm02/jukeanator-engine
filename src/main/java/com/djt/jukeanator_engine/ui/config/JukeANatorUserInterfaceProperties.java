package com.djt.jukeanator_engine.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user-interface")
public class JukeANatorUserInterfaceProperties {

  private boolean enabled = false; // if true, a JFC/Swing UI is launched, otherwise, a headless backend
  private String baseUrl = "http://localhost:8080";

  //
  // CREDIT CONFIGURATION
  //
  private int creditsPer = 3;
  private int fiveBonusCredits = 3;
  private int tenBonusCredits = 10;

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

  public int getCreditsPer() {
    return creditsPer;
  }

  public void setCreditsPer(int creditsPer) {
    this.creditsPer = creditsPer;
  }

  public int getFiveBonusCredits() {
    return fiveBonusCredits;
  }

  public void setFiveBonusCredits(int fiveBonusCredits) {
    this.fiveBonusCredits = fiveBonusCredits;
  }

  public int getTenBonusCredits() {
    return tenBonusCredits;
  }

  public void setTenBonusCredits(int tenBonusCredits) {
    this.tenBonusCredits = tenBonusCredits;
  }
}