package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenRequest;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.exceptions.NotFoundException;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;

/**
 * The real, Braintree-backed {@link PaymentGateway}. Only ever constructed on a master instance
 * (see {@code AppConfig.braintreeGateway}/{@code AppConfig.braintreePaymentGateway}) — payment
 * processing is centralized to the one master-mode Braintree merchant account for the whole
 * multi-location business, so a standalone/slave instance never holds Braintree credentials and
 * gets {@link NoOpPaymentGateway} instead.
 */
public class BraintreePaymentGateway implements PaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(BraintreePaymentGateway.class);

  /** Default {@code braintree.customer-id-prefix}. */
  public static final String DEFAULT_CUSTOMER_ID_PREFIX = "jukeanator-";

  private final BraintreeGateway braintreeGateway;
  private final String customerIdPrefix;

  public BraintreePaymentGateway(BraintreeGateway braintreeGateway) {
    this(braintreeGateway, DEFAULT_CUSTOMER_ID_PREFIX);
  }

  /**
   * {@code customerIdPrefix} goes in front of each patron's user id to make their Braintree
   * customer id, so two servers sharing one Braintree account (e.g. a developer's master and
   * production, both on the sandbox) never share customers.
   */
  public BraintreePaymentGateway(BraintreeGateway braintreeGateway, String customerIdPrefix) {
    this.braintreeGateway = Objects.requireNonNull(braintreeGateway);
    this.customerIdPrefix = customerIdPrefix == null || customerIdPrefix.isBlank()
        ? DEFAULT_CUSTOMER_ID_PREFIX
        : customerIdPrefix.trim();
  }

  @Override
  public String generateClientToken() {
    return braintreeGateway.clientToken().generate();
  }

  /** Creates the customer on first use, so the token can list and remove their stored cards. */
  @Override
  public String generateClientToken(String customerKey) {
    String customerId = customerIdFor(customerKey);
    ensureCustomer(customerId);
    return braintreeGateway.clientToken().generate(new ClientTokenRequest().customerId(customerId));
  }

  @Override
  public PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce) {
    return sale(new TransactionRequest()
        .amount(amount)
        .paymentMethodNonce(paymentMethodNonce)
        .options().submitForSettlement(true).done());
  }

  @Override
  public PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce,
      String customerKey, boolean storeCard) {

    String customerId = customerIdFor(customerKey);
    if (storeCard) {
      try {
        ensureCustomer(customerId);
      } catch (RuntimeException e) {
        return PaymentChargeResult.failure("Payment processing failed: " + e.getMessage());
      }
    }
    return sale(new TransactionRequest()
        .amount(amount)
        .paymentMethodNonce(paymentMethodNonce)
        .customerId(customerId)
        .options().submitForSettlement(true).storeInVaultOnSuccess(storeCard).done());
  }

  @Override
  public void deleteCustomer(String customerKey) {

    String customerId = customerIdFor(customerKey);
    try {
      Result<Customer> result = braintreeGateway.customer().delete(customerId);
      if (!result.isSuccess()) {
        log.warn("Braintree refused to delete customer {}: {}", customerId, result.getMessage());
      }
    } catch (NotFoundException e) {
      // Never stored a card -- nothing to delete.
    } catch (RuntimeException e) {
      log.warn("Could not delete Braintree customer {}", customerId, e);
    }
  }

  String customerIdFor(String customerKey) {
    if (customerKey == null || customerKey.isBlank()) {
      throw new IllegalArgumentException("customerKey cannot be blank");
    }
    return customerIdPrefix + customerKey.trim();
  }

  private void ensureCustomer(String customerId) {

    try {
      braintreeGateway.customer().find(customerId);
      return;
    } catch (NotFoundException e) {
      // First stored-card visit: create it below.
    }
    Result<Customer> result =
        braintreeGateway.customer().create(new CustomerRequest().id(customerId));
    if (!result.isSuccess()) {
      throw new PaymentException("Could not set up stored cards: " + result.getMessage());
    }
  }

  private PaymentChargeResult sale(TransactionRequest txRequest) {

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
