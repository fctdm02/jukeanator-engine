package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;

/**
 * The real, Braintree-backed {@link PaymentGateway}. Only ever constructed on a master instance
 * (see {@code AppConfig.braintreeGateway}/{@code AppConfig.braintreePaymentGateway}) — payment
 * processing is centralized to the one master-mode Braintree merchant account for the whole
 * multi-location business, so a standalone/slave instance never holds Braintree credentials and
 * gets {@link NoOpPaymentGateway} instead.
 */
public class BraintreePaymentGateway implements PaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(BraintreePaymentGateway.class);

  private final BraintreeGateway braintreeGateway;

  public BraintreePaymentGateway(BraintreeGateway braintreeGateway) {
    this.braintreeGateway = Objects.requireNonNull(braintreeGateway);
  }

  @Override
  public String generateClientToken() {
    return braintreeGateway.clientToken().generate();
  }

  @Override
  public PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce) {

    TransactionRequest txRequest = new TransactionRequest()
        .amount(amount)
        .paymentMethodNonce(paymentMethodNonce)
        .options().submitForSettlement(true).done();

    Result<Transaction> result;
    try {
      result = braintreeGateway.transaction().sale(txRequest);
    } catch (RuntimeException e) {
      return PaymentChargeResult.failure("Payment processing failed: " + e.getMessage());
    }

    if (!result.isSuccess()) {
      return PaymentChargeResult.failure("Payment declined: " + result.getMessage());
    }

    Transaction tx = result.getTarget();
    return PaymentChargeResult.success(tx.getId(), describePaymentSource(tx));
  }

  /**
   * Voids the sale. {@link #charge} submits for settlement, and a sale stays voidable until
   * Braintree settles it (hours later), so a void issued right after the charge succeeds.
   */
  @Override
  public boolean voidCharge(String transactionId) {

    try {
      Result<Transaction> result = braintreeGateway.transaction().voidTransaction(transactionId);
      if (result.isSuccess()) {
        return true;
      }
      log.error("Braintree refused to void transaction {}: {} -- refund it manually",
          transactionId, result.getMessage());
    } catch (RuntimeException e) {
      log.error("Could not void Braintree transaction {} -- refund it manually", transactionId, e);
    }
    return false;
  }

  /**
   * Maps a completed Braintree transaction's payment instrument to a short display string for the
   * receipt/success screen, e.g. {@code "Visa •••• 1234"}, {@code "PayPal"}, {@code "Venmo"}.
   */
  private static String describePaymentSource(Transaction tx) {

    if (tx.getCreditCard() != null && tx.getCreditCard().getCardType() != null) {
      String last4 = tx.getCreditCard().getLast4();
      return tx.getCreditCard().getCardType() + (last4 != null ? " •••• " + last4 : "");
    }
    if (tx.getPayPalDetails() != null) {
      return "PayPal";
    }
    if (tx.getVenmoAccountDetails() != null) {
      return "Venmo";
    }
    if (tx.getAndroidPayDetails() != null) {
      return "Google Pay";
    }
    if (tx.getApplePayDetails() != null) {
      return "Apple Pay";
    }
    return tx.getPaymentInstrumentType() != null ? tx.getPaymentInstrumentType() : "Unknown";
  }
}
