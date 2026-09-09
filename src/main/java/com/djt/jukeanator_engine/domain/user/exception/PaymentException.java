package com.djt.jukeanator_engine.domain.user.exception;

/**
 * Thrown for anything that stops an Add Funds purchase from completing: an unknown package, a
 * missing payment method nonce, a declined/failed Braintree transaction, or a wrapped Braintree
 * SDK error. Never thrown after credits have actually been granted.
 */
public class PaymentException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public PaymentException(String message) {
    super(message);
  }

  public PaymentException(String message, Throwable cause) {
    super(message, cause);
  }
}
