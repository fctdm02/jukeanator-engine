package com.djt.jukeanator_engine.domain.license.legacy;

/**
 * Decrypted contents of a legacy {@code ExtendCode.CTL} subscription file.
 *
 * @param expirationQuarter quarter in {@code nQyyyy} form, e.g. {@code 4Q2027}
 * @param hoursRemaining runtime hours left; decremented once per hour by the running application
 * @param machineId machine id (legacy "sid") the file was written for
 * @param customerEmail registered customer email the file was written for
 */
public record ExtendCode(
    String expirationQuarter,
    int hoursRemaining,
    String machineId,
    String customerEmail) {

  public ExtendCode withHoursRemaining(int hours) {
    return new ExtendCode(expirationQuarter, hours, machineId, customerEmail);
  }
}
