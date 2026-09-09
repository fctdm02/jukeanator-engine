package com.djt.jukeanator_engine.domain.user.service;

/** Outcome of a {@link PaymentGateway#charge} call — never an exception, always one of these two shapes. */
public record PaymentChargeResult(boolean success, String transactionId, String paymentSource,
    String failureMessage) {

  public static PaymentChargeResult success(String transactionId, String paymentSource) {
    return new PaymentChargeResult(true, transactionId, paymentSource, null);
  }

  public static PaymentChargeResult failure(String failureMessage) {
    return new PaymentChargeResult(false, null, null, failureMessage);
  }
}
