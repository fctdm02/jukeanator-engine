package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;

/**
 * Standalone/slave stand-in: payment processing is centralized to the master instance's Braintree
 * merchant account (see {@code AppConfig.braintreePaymentGateway}) — a standalone/slave instance
 * never holds Braintree credentials and never constructs a real {@code BraintreeGateway} at all.
 * Both methods here throw rather than silently no-op, since reaching either one means Add Funds
 * was invoked somewhere it's not meant to be reachable from.
 */
public class NoOpPaymentGateway implements PaymentGateway {

  private static final String MESSAGE = "Add Funds is only available on the master instance";

  @Override
  public String generateClientToken() {
    throw new PaymentException(MESSAGE);
  }

  @Override
  public PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce) {
    throw new PaymentException(MESSAGE);
  }
}
