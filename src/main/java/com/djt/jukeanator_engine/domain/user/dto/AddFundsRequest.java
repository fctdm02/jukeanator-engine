package com.djt.jukeanator_engine.domain.user.dto;

/**
 * {@code paymentMethodNonce} is the single-use Braintree tokenization result from whichever
 * payment method the client's Braintree Web SDK component resolved (Hosted Fields, PayPal
 * Checkout, Venmo, Google Pay, or Apple Pay) — see the web UI's payment-method sheet.
 */
public record AddFundsRequest(String packageId, String paymentMethodNonce) {}
