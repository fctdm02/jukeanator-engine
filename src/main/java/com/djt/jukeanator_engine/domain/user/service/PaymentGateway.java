package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;

/**
 * Abstracts the Add Funds payment processor away from any specific vendor SDK (Braintree today).
 * Payment processing is centralized to the master instance's merchant account for the whole
 * multi-location business — see {@code BraintreePaymentGateway} (master-only) and {@code
 * NoOpPaymentGateway} (standalone/slave), wired conditionally in {@code AppConfig}.
 *
 * <p>Stored cards: each patron has a customer record at the payment processor, identified by a
 * {@code customerKey} (the patron's own user id; the gateway adds its configured prefix). A client
 * token generated for that customer lets the web UI list, pay with and remove the patron's stored
 * cards; {@link #charge(BigDecimal, String, String, boolean)} stores a new card when asked.
 */
public interface PaymentGateway {

  /** A client token for the web UI's payment-method sheet to initialize its SDK with. */
  String generateClientToken();

  /**
   * A client token for {@code customerKey}'s own payment-method sheet, which can list, pay with
   * and remove that customer's stored cards. Creates the customer record on first use.
   */
  default String generateClientToken(String customerKey) {
    return generateClientToken();
  }

  /** Charges {@code amount} against {@code paymentMethodNonce}. Never throws for a decline. */
  PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce);

  /**
   * Charges like {@link #charge(BigDecimal, String)}, for {@code customerKey}; when
   * {@code storeCard} is true, a successful charge also stores the card for that customer's next
   * purchase. Never throws for a decline.
   */
  default PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce,
      String customerKey, boolean storeCard) {
    return charge(amount, paymentMethodNonce);
  }

  /**
   * Cancels a successful {@link #charge} that could not be recorded, so the customer is never
   * billed for credits they did not receive. Returns whether it was cancelled. Never throws.
   */
  boolean voidCharge(String transactionId);

  /**
   * Deletes {@code customerKey}'s customer record and every card stored for it, when the patron
   * closes their account. Does nothing when there is none. Never throws.
   */
  default void deleteCustomer(String customerKey) {
  }
}
