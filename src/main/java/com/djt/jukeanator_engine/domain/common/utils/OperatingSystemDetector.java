package com.djt.jukeanator_engine.domain.common.utils;

import java.util.Locale;

public class OperatingSystemDetector {

  public enum OSType {
    WINDOWS, LINUX, MACOS, UNKNOWN
  }

  private static final OSType DETECTED_OS;

  static {

    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      DETECTED_OS = OSType.WINDOWS;
    } else if (os.startsWith("mac") || os.startsWith("darwin")) {
      DETECTED_OS = OSType.MACOS;
    } else if (os.startsWith("linux")) {
      DETECTED_OS = OSType.LINUX;
    } else {
      DETECTED_OS = OSType.UNKNOWN;
    }
  }

  public static OSType getOperatingSystem() {
    return DETECTED_OS;
  }

  public static void main(String[] args) {
    System.out.println("Running on: " + getOperatingSystem());
  }
}
