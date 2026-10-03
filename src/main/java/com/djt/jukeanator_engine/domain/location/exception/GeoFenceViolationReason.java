package com.djt.jukeanator_engine.domain.location.exception;

/** Why a Web/Mobile UI queue operation was refused by the geo-fence. */
public enum GeoFenceViolationReason {

  /** The request carried no (or a malformed) device position. */
  POSITION_REQUIRED,

  /** The device position was taken too long ago, or claims to be from the future. */
  POSITION_TOO_OLD,

  /** The device reported an accuracy radius larger than {@code app.geo-fence.max-accuracy-meters}. */
  POSITION_TOO_INACCURATE,

  /** A hand-entered position was sent while {@code app.geo-fence.allow-simulated-position} is off. */
  SIMULATED_POSITION_NOT_ALLOWED,

  /** The device is farther from the location than the fence allows. */
  OUTSIDE_FENCE
}
