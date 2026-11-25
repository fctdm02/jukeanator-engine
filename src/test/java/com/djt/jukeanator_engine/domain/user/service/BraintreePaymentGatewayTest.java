package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.braintreegateway.AndroidPayDetails;
import com.braintreegateway.ApplePayDetails;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenGateway;
import com.braintreegateway.ClientTokenRequest;
import com.braintreegateway.CreditCard;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerGateway;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.exceptions.NotFoundException;
import org.mockito.ArgumentCaptor;
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

  @Test
  void voidCharge_voidsTheTransactionAtBraintree() {
    Result<Transaction> voided = mock(Result.class);
    when(voided.isSuccess()).thenReturn(true);
    when(transactionGateway.voidTransaction("txn-1")).thenReturn(voided);

    assertTrue(paymentGateway.voidCharge("txn-1"));
    verify(transactionGateway).voidTransaction("txn-1");
  }

  @Test
  void voidCharge_returnsFalseWhenBraintreeRefuses() {
    Result<Transaction> refused = mock(Result.class);
    when(refused.isSuccess()).thenReturn(false);
    when(refused.getMessage()).thenReturn("Transaction can only be voided if status is authorized");
    when(transactionGateway.voidTransaction("txn-1")).thenReturn(refused);

    assertFalse(paymentGateway.voidCharge("txn-1"));
  }

  @Test
  void voidCharge_returnsFalseInsteadOfThrowingWhenTheGatewayFails() {
    when(transactionGateway.voidTransaction("txn-1")).thenThrow(new RuntimeException("network error"));

    assertFalse(paymentGateway.voidCharge("txn-1"));
  }

  // ── stored cards ──────────────────────────────────────────────────────────

  private CustomerGateway customerGateway(boolean customerExists) {
    CustomerGateway customers = mock(CustomerGateway.class);
    when(braintreeGateway.customer()).thenReturn(customers);
    if (customerExists) {
      when(customers.find("jukeanator-42")).thenReturn(mock(Customer.class));
    } else {
      when(customers.find("jukeanator-42")).thenThrow(new NotFoundException());
    }
    Result<Customer> created = mock(Result.class);
    when(created.isSuccess()).thenReturn(true);
    when(customers.create(any(CustomerRequest.class))).thenReturn(created);
    return customers;
  }

  @Test
  void generateClientTokenForACustomer_createsTheCustomerOnFirstUse_andScopesTheToken() {
    CustomerGateway customers = customerGateway(false);
    ClientTokenGateway clientTokenGateway = mock(ClientTokenGateway.class);
    when(braintreeGateway.clientToken()).thenReturn(clientTokenGateway);
    when(clientTokenGateway.generate(any(ClientTokenRequest.class))).thenReturn("customer-token");

    assertEquals("customer-token", paymentGateway.generateClientToken("42"));

    ArgumentCaptor<CustomerRequest> created = ArgumentCaptor.forClass(CustomerRequest.class);
    verify(customers).create(created.capture());
    assertTrue(created.getValue().toXML().contains("jukeanator-42"));
    ArgumentCaptor<ClientTokenRequest> token = ArgumentCaptor.forClass(ClientTokenRequest.class);
    verify(clientTokenGateway).generate(token.capture());
    assertTrue(token.getValue().toXML().contains("jukeanator-42"));
  }

  @Test
  void generateClientTokenForAnExistingCustomer_doesNotCreateItAgain() {
    CustomerGateway customers = customerGateway(true);
    ClientTokenGateway clientTokenGateway = mock(ClientTokenGateway.class);
    when(braintreeGateway.clientToken()).thenReturn(clientTokenGateway);
    when(clientTokenGateway.generate(any(ClientTokenRequest.class))).thenReturn("customer-token");

    paymentGateway.generateClientToken("42");

    verify(customers, never()).create(any(CustomerRequest.class));
  }

  @Test
  void theCustomerIdPrefixIsConfigurable() {
    assertEquals("dev-7", new BraintreePaymentGateway(braintreeGateway, "dev-").customerIdFor("7"));
    assertEquals("jukeanator-7", new BraintreePaymentGateway(braintreeGateway, " ").customerIdFor("7"));
  }

  @Test
  void chargeStoringTheCard_chargesForTheCustomer_andStoresTheCardOnSuccess() {
    customerGateway(true);
    stubSuccessfulSale();

    PaymentChargeResult result =
        paymentGateway.charge(new BigDecimal("7.00"), "fake-nonce", "42", true);

    assertTrue(result.success());
    ArgumentCaptor<TransactionRequest> sale = ArgumentCaptor.forClass(TransactionRequest.class);
    verify(transactionGateway).sale(sale.capture());
    String xml = sale.getValue().toXML();
    assertTrue(xml.contains("jukeanator-42"), xml);
    assertTrue(xml.matches("(?s).*<storeInVaultOnSuccess[^>]*>true</storeInVaultOnSuccess>.*"), xml);
  }

  @Test
  void chargeStoringTheCard_failsCleanlyWhenTheCustomerCannotBeSetUp() {
    CustomerGateway customers = mock(CustomerGateway.class);
    when(braintreeGateway.customer()).thenReturn(customers);
    when(customers.find("jukeanator-42")).thenThrow(new RuntimeException("network error"));

    PaymentChargeResult result =
        paymentGateway.charge(new BigDecimal("7.00"), "fake-nonce", "42", true);

    assertFalse(result.success());
    verify(transactionGateway, never()).sale(any(TransactionRequest.class));
  }

  @Test
  void deleteCustomer_deletesIt_andIgnoresACustomerThatNeverExisted() {
    CustomerGateway customers = mock(CustomerGateway.class);
    when(braintreeGateway.customer()).thenReturn(customers);
    Result<Customer> deleted = mock(Result.class);
    when(deleted.isSuccess()).thenReturn(true);
    when(customers.delete("jukeanator-42")).thenReturn(deleted);
    when(customers.delete("jukeanator-43")).thenThrow(new NotFoundException());

    paymentGateway.deleteCustomer("42");
    paymentGateway.deleteCustomer("43"); // must not throw

    verify(customers).delete("jukeanator-42");
  }

  private void stubSuccessfulSale() {
    Transaction tx = mock(Transaction.class);
    when(tx.getId()).thenReturn("txn-1");
    Result<Transaction> success = mock(Result.class);
    when(success.isSuccess()).thenReturn(true);
    when(success.getTarget()).thenReturn(tx);
    when(transactionGateway.sale(any(TransactionRequest.class))).thenReturn(success);
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
