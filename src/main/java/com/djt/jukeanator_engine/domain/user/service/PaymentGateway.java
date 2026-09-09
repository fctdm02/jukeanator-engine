package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;

/**
 * Abstracts the Add Funds payment processor away from any specific vendor SDK (Braintree today).
 * Payment processing is centralized to the master instance's merchant account for the whole
 * multi-location business — see {@code BraintreePaymentGateway} (master-only) and {@code
 * NoOpPaymentGateway} (standalone/slave), wired conditionally in {@code AppConfig}.
 */
public interface PaymentGateway {

  /** A client token for the web UI's payment-method sheet to initialize its SDK with. */
  String generateClientToken();

  /** Charges {@code amount} against {@code paymentMethodNonce}. Never throws for a decline. */
  PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce);
}
