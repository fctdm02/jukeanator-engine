package com.djt.jukeanator_engine.domain.financialledger.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * One Web/Mobile Add Funds purchase for a location: a patron paid {@link #getAmount()} through
 * Braintree for song credits usable only at {@link #getLocationId()}. Like a swipe on the
 * location's own credit-card reader, the whole purchase is that location's revenue the moment it
 * is made, so it is recorded once, in full, here -- never per song as the credits are spent.
 *
 * <p>Recorded on master (where Braintree charges are taken) with {@code sourceTransactionId}
 * {@code null}, then mirrored to the location's slave (see {@code MobileTransactionSlaveNotifier}
 * and {@code FinancialLedgerSyncService}), where the copy carries master's
 * {@code persistentIdentity} as its {@code sourceTransactionId}. The mirror direction is the
 * reverse of cash and card, so a slave never pushes these rows back to master.
 */
@Entity
@DiscriminatorValue(LocalMobileTransactionEntity.DISCRIMINATOR)
public class LocalMobileTransactionEntity extends AbstractLocationTransactionEntity {

  private static final long serialVersionUID = 1L;

  public static final String DISCRIMINATOR = "MOBILE_WEB";

  @Column(name = "user_email")
  private String userEmail;

  // The payment gateway's transaction id (e.g. Braintree's), for reconciling against its reports.
  @Column(name = "payment_reference")
  private String paymentReference;

  @Column(name = "package_code", length = 64)
  private String packageCode;

  @Column(name = "credits_awarded")
  private Integer creditsAwarded;

  @Column(name = "bonus_credits")
  private Integer bonusCredits;

  protected LocalMobileTransactionEntity() {} // for JPA

  public LocalMobileTransactionEntity(Integer persistentIdentity, BigDecimal amount,
      Instant timestamp, Integer locationId, Integer sourceTransactionId, String userEmail,
      String paymentReference, String packageCode, Integer creditsAwarded, Integer bonusCredits) {
    super(persistentIdentity, amount, timestamp, locationId, sourceTransactionId);
    this.userEmail = userEmail;
    this.paymentReference = paymentReference;
    this.packageCode = packageCode;
    this.creditsAwarded = creditsAwarded;
    this.bonusCredits = bonusCredits;
  }

  /** Never pushed slave-to-master: master is where these are recorded. */
  @Override
  public boolean isPendingMasterSync() {
    return false;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public String getPackageCode() {
    return packageCode;
  }

  public Integer getCreditsAwarded() {
    return creditsAwarded;
  }

  public Integer getBonusCredits() {
    return bonusCredits;
  }

  @Override
  public String getNaturalIdentity() {
    return "LocalMobileTransaction/" + getTimestamp() + "/" + getPersistentIdentity();
  }
}
