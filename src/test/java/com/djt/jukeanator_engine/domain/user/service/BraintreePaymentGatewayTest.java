package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.braintreegateway.AndroidPayDetails;
import com.braintreegateway.ApplePayDetails;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenGateway;
import com.braintreegateway.CreditCard;
import com.braintreegateway.PayPalDetails;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionGateway;
import com.braintreegateway.TransactionRequest;
import com.braintreegateway.VenmoAccountDetails;

@SuppressWarnings("unchecked")
class BraintreePaymentGatewayTest {

  private BraintreeGateway braintreeGateway;
  private TransactionGateway transactionGateway;
  private BraintreePaymentGateway paymentGateway;

  @BeforeEach
  void setUp() {
    braintreeGateway = mock(BraintreeGateway.class);
    transactionGateway = mock(TransactionGateway.class);
    when(braintreeGateway.transaction()).thenReturn(transactionGateway);
    paymentGateway = new BraintreePaymentGateway(braintreeGateway);
  }

  @Test
  void generateClientToken_delegatesToBraintreeClientTokenGateway() {
    ClientTokenGateway clientTokenGateway = mock(ClientTokenGateway.class);
    when(braintreeGateway.clientToken()).thenReturn(clientTokenGateway);
    when(clientTokenGateway.generate()).thenReturn("fake-client-token");

    assertEquals("fake-client-token", paymentGateway.generateClientToken());
  }

  @Test
  void charge_returnsFailureWhenDeclined() {
    Result<Transaction> declined = mock(Result.class);
    when(declined.isSuccess()).thenReturn(false);
    when(declined.getMessage()).thenReturn("Do Not Honor");
    when(transactionGateway.sale(any(TransactionRequest.class))).thenReturn(declined);

    PaymentChargeResult result = paymentGateway.charge(new BigDecimal("7.00"), "fake-nonce");

    assertFalse(result.success());
    assertTrue(result.failureMessage().contains("Do Not Honor"));
  }

  @Test
  void charge_returnsFailureWhenGatewayThrows() {
    when(transactionGateway.sale(any(TransactionRequest.class)))
        .thenThrow(new RuntimeException("network error"));

    PaymentChargeResult result = paymentGateway.charge(new BigDecimal("7.00"), "fake-nonce");

    assertFalse(result.success());
    assertTrue(result.failureMessage().contains("network error"));
  }

  @Test
  void charge_mapsCreditCardPaymentSource() {
    CreditCard creditCard = mock(CreditCard.class);
    when(creditCard.getCardType()).thenReturn("Visa");
    when(creditCard.getLast4()).thenReturn("1234");

    PaymentChargeResult result = chargeWithTransactionStub(tx -> when(tx.getCreditCard()).thenReturn(creditCard));

    assertEquals("Visa •••• 1234", result.paymentSource());
  }

  @Test
  void charge_mapsPayPalPaymentSource() {
    PaymentChargeResult result =
        chargeWithTransactionStub(tx -> when(tx.getPayPalDetails()).thenReturn(mock(PayPalDetails.class)));

    assertEquals("PayPal", result.paymentSource());
  }

  @Test
  void charge_mapsVenmoPaymentSource() {
    PaymentChargeResult result = chargeWithTransactionStub(
        tx -> when(tx.getVenmoAccountDetails()).thenReturn(mock(VenmoAccountDetails.class)));

    assertEquals("Venmo", result.paymentSource());
  }

  @Test
  void charge_mapsGooglePayPaymentSource() {
    PaymentChargeResult result = chargeWithTransactionStub(
        tx -> when(tx.getAndroidPayDetails()).thenReturn(mock(AndroidPayDetails.class)));

    assertEquals("Google Pay", result.paymentSource());
  }

  @Test
  void charge_mapsApplePayPaymentSource() {
    PaymentChargeResult result =
        chargeWithTransactionStub(tx -> when(tx.getApplePayDetails()).thenReturn(mock(ApplePayDetails.class)));

    assertEquals("Apple Pay", result.paymentSource());
  }

  @Test
  void charge_fallsBackToPaymentInstrumentTypeWhenUnrecognized() {
    PaymentChargeResult result =
        chargeWithTransactionStub(tx -> when(tx.getPaymentInstrumentType()).thenReturn("some_new_method"));

    assertEquals("some_new_method", result.paymentSource());
  }

  private PaymentChargeResult chargeWithTransactionStub(Consumer<Transaction> stub) {
    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn("txn-1");
    stub.accept(tx);

    Result<Transaction> success = mock(Result.class);
    when(success.isSuccess()).thenReturn(true);
    when(success.getTarget()).thenReturn(tx);
    when(transactionGateway.sale(any(TransactionRequest.class))).thenReturn(success);

    return paymentGateway.charge(new BigDecimal("7.00"), "fake-nonce");
  }
}
