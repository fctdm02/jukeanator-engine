package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.user.exception.PaymentException;

class NoOpPaymentGatewayTest {

  private final NoOpPaymentGateway gateway = new NoOpPaymentGateway();

  @Test
  void generateClientToken_throwsPaymentException() {
    assertThrows(PaymentException.class, gateway::generateClientToken);
  }

  @Test
  void charge_throwsPaymentException() {
    assertThrows(PaymentException.class,
        () -> gateway.charge(new BigDecimal("7.00"), "fake-nonce"));
  }
}
