package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A plain interface (not just a concrete class) so {@code @Async} can proxy it via the JDK
 * interface-proxy mode this app runs under (see {@code spring.aop.proxy-target-class: false} in
 * application.yml) — a concrete class there would silently fail to proxy.
 */
public interface EmailService {

  void sendPurchaseReceiptEmail(String toAddress, String firstName, int credits, int bonusCredits,
      BigDecimal amountUsd, String paymentSource, String transactionId, Instant timestamp,
      int newBalance);
}
