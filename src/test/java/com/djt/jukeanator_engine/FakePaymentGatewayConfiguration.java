package com.djt.jukeanator_engine;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.djt.jukeanator_engine.domain.user.service.PaymentChargeResult;
import com.djt.jukeanator_engine.domain.user.service.PaymentGateway;

/**
 * Overrides {@code AppConfig}'s real {@code BraintreePaymentGateway} (wired unconditionally
 * whenever {@code app.mode=master}) with a same-process fake whose charges always succeed -- a
 * master-mode test must never make a real network call to Braintree, and the fake sandbox
 * credentials in {@code application-test.yml} wouldn't authenticate against it anyway. Lets such a
 * test give a patron credits the only way production does: through Add Funds.
 */
@TestConfiguration
public class FakePaymentGatewayConfiguration {

  @Bean
  @Primary
  PaymentGateway fakePaymentGateway() {
    return new PaymentGateway() {

      @Override
      public String generateClientToken() {
        return "fake-client-token";
      }

      @Override
      public PaymentChargeResult charge(BigDecimal amount, String paymentMethodNonce) {
        return PaymentChargeResult.success("fake-txn-" + UUID.randomUUID(), "TestGateway");
      }

      @Override
      public boolean voidCharge(String transactionId) {
        return true;
      }
    };
  }
}
