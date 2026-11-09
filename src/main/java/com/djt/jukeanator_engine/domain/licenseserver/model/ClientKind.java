package com.djt.jukeanator_engine.domain.licenseserver.model;

/** Which application last contacted the license server for a machine. */
public enum ClientKind {
  /** Legacy MP3Jukeboxx desktop application. */
  LEGACY,
  /** jukeanator-engine (standalone or slave). */
  ENGINE
}
