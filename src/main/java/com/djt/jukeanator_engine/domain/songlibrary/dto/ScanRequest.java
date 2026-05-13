package com.djt.jukeanator_engine.domain.songlibrary.dto;

public class ScanRequest {

  private String scanPath;

  public ScanRequest() {}

  public ScanRequest(String scanPath) {
    this.scanPath = scanPath;
  }

  public String getScanPath() {
    return scanPath;
  }

  public void setScanPath(String scanPath) {
    this.scanPath = scanPath;
  }
}
