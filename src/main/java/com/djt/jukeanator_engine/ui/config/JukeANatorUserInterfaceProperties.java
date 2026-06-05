package com.djt.jukeanator_engine.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user-interface")
public class JukeANatorUserInterfaceProperties {

  private boolean enabled = false; // if true, a JFC/Swing UI is launched, otherwise, a headless
                                   // backend

  //
  // CREDIT CONFIGURATION
  //
  private char incrementCreditsKey = 'a';
  private int numCredits = 6; 
  private int creditsPerDollar = 3;
  private int fiveDollarBonusCredits = 3;
  private int tenDollarBonusCredits = 10;

  //
  // SEARCH CONFIGURATION
  //
  private boolean enableTypeAheadSearch = true;

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
  
  public char getIncrementCreditsKey() {
    return incrementCreditsKey;
  }

  public void setIncrementCreditsKey(char incrementCreditsKey) {
    this.incrementCreditsKey = incrementCreditsKey;
  }

  public int getNumCredits() {
    return numCredits;
  }

  public void setNumCredits(int numCredits) {
    this.numCredits = numCredits;
  }

  public int getCreditsPerDollar() {
    return creditsPerDollar;
  }

  public void setCreditsPerDollar(int creditsPerDollar) {
    this.creditsPerDollar = creditsPerDollar;
  }

  public int getFiveDollarBonusCredits() {
    return fiveDollarBonusCredits;
  }

  public void setFiveDollarBonusCredits(int fiveDollarBonusCredits) {
    this.fiveDollarBonusCredits = fiveDollarBonusCredits;
  }

  public int getTenDollarBonusCredits() {
    return tenDollarBonusCredits;
  }

  public void setTenDollarBonusCredits(int tenDollarBonusCredits) {
    this.tenDollarBonusCredits = tenDollarBonusCredits;
  }

  public boolean isEnableTypeAheadSearch() {
    return enableTypeAheadSearch;
  }

  public void setEnableTypeAheadSearch(boolean enableTypeAheadSearch) {
    this.enableTypeAheadSearch = enableTypeAheadSearch;
  }
}
