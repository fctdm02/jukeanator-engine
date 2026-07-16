package com.djt.jukeanator_engine.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user-interface")
public class JukeANatorUserInterfaceProperties {

  private boolean alwaysOnTop = false;
  
  //
  // SCREEN SAVER
  // 
  private boolean enableScreenSaver = true;
  
  //
  // HIBERNATION
  //  
  private boolean enableHibernation = false;
  private int hibernateBegin = 3; // In 24 hour/military time (e.g. 3:00 hours is 3:00AM) Only used when enableHibernation is true
  private int hibernateEnd = 10; // In 24 hour/military time (e.g. 10:00 hours is 10:00AM) Only used when enableHibernation is true  

  //
  // CREDIT CONFIGURATION
  //
  private char incrementCreditsKey = 'a';
  private int numCredits = 6;
  private int priorityCostMultiplier = 2;
  private int creditsPerDollar = 3;
  private int fiveDollarBonusCredits = 3;
  private int tenDollarBonusCredits = 10;

  //
  // SEARCH CONFIGURATION
  //
  private boolean enableTypeAheadSearch = true;

  
  
  public boolean isAlwaysOnTop() {
    return alwaysOnTop;
  }

  public void setAlwaysOnTop(boolean alwaysOnTop) {
    this.alwaysOnTop = alwaysOnTop;
  }

  public boolean isEnableScreenSaver() {
    return enableScreenSaver;
  }

  public void setEnableScreenSaver(boolean enableScreenSaver) {
    this.enableScreenSaver = enableScreenSaver;
  }

  public boolean isEnableHibernation() {
    return enableHibernation;
  }

  public void setEnableHibernation(boolean enableHibernation) {
    this.enableHibernation = enableHibernation;
  }

  public int getHibernateBegin() {
    return hibernateBegin;
  }

  public void setHibernateBegin(int hibernateBegin) {
    this.hibernateBegin = hibernateBegin;
  }

  public int getHibernateEnd() {
    return hibernateEnd;
  }

  public void setHibernateEnd(int hibernateEnd) {
    this.hibernateEnd = hibernateEnd;
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

  public int getPriorityCostMultiplier() {
    return priorityCostMultiplier;
  }

  public void setPriorityCostMultiplier(int priorityCostMultiplier) {
    this.priorityCostMultiplier = priorityCostMultiplier;
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
