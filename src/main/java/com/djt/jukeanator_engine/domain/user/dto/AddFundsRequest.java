package com.djt.jukeanator_engine.domain.user.dto;

/**
 * {@code locationId} is the location the credits are bought for -- the only location where they
 * can be used, and the location whose financial ledger records the purchase.
 * {@code paymentMethodNonce} is the single-use Braintree tokenization result from whichever
 * payment method the client's Braintree Web SDK component resolved (a stored card, Hosted Fields,
 * PayPal Checkout, Venmo, Google Pay, or Apple Pay) — see the web UI's payment-method sheet.
 * {@code saveCard} asks for a newly entered card to be stored for the patron's next purchase; a
 * client that predates stored cards omits it (null, treated as false).
 */
public record AddFundsRequest(Integer locationId, String packageId, String paymentMethodNonce,
    Boolean saveCard) {

  /** A purchase that does not store the card. */
  public AddFundsRequest(Integer locationId, String packageId, String paymentMethodNonce) {
    this(locationId, packageId, paymentMethodNonce, Boolean.FALSE);
  }

  public boolean storeCard() {
    return Boolean.TRUE.equals(saveCard);
  }
}
