package com.djt.jukeanator_engine.domain.user.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only Add-Funds record, owned by the {@link UserEntity} it belongs to: a user spending
 * real money (charged via {@code PaymentGateway}, e.g. Braintree) to obtain song credits usable at
 * any location. Deliberately distinct from {@link UserSongCreditUsageEntity}, which records the
 * later, separate act of spending already-owned song credits to queue a song at a particular
 * location -- see this package's design note in {@code UserServiceImpl} for why the two must never
 * be conflated under one ambiguous "credit transaction" concept.
 *
 * <p>No {@code locationId} -- funds added here are not coupled to any location; only the later
 * song-credit spend is (see {@link UserSongCreditUsageEntity#getLocationId()}).
 *
 * @author tmyers
 */
@Entity
@Table(name = "user_add_funds_transaction")
public class UserAddFundsTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  // Persistence-only back-reference -- the FK column JPA needs to own the UserEntity <->
  // transaction relationship. UserEntity.addUserAddFundsTransaction() is the single place that
  // keeps this back-reference in sync (see setUser()).
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @Column(name = "package_id")
  private String packageId;

  @Column(name = "credits_awarded", nullable = false)
  private int creditsAwarded;

  @Column(name = "bonus_credits", nullable = false)
  private int bonusCredits;

  @Column(name = "amount_usd", precision = 12, scale = 2, nullable = false)
  private BigDecimal amountUsd;

  @Column(name = "payment_source")
  private String paymentSource;

  @Column(name = "payment_transaction_id")
  private String paymentTransactionId;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "resulting_balance", nullable = false)
  private int resultingBalance;

  protected UserAddFundsTransactionEntity() {} // for JPA

  public UserAddFundsTransactionEntity(Integer persistentIdentity, String packageId,
      int creditsAwarded, int bonusCredits, BigDecimal amountUsd, String paymentSource,
      String paymentTransactionId, Instant timestamp, int resultingBalance) {
    super(persistentIdentity);
    this.packageId = packageId;
    this.creditsAwarded = creditsAwarded;
    this.bonusCredits = bonusCredits;
    this.amountUsd = amountUsd;
    this.paymentSource = paymentSource;
    this.paymentTransactionId = paymentTransactionId;
    this.timestamp = timestamp;
    this.resultingBalance = resultingBalance;
  }

  void setUser(UserEntity user) {
    this.user = user;
  }

  @Override
  public String getNaturalIdentity() {
    return getUserEmail() + "/" + timestamp + "/" + getPersistentIdentity();
  }

  public String getUserEmail() {
    return user != null ? user.getEmailAddress() : null;
  }

  public String getPackageId() {
    return packageId;
  }

  public int getCreditsAwarded() {
    return creditsAwarded;
  }

  public int getBonusCredits() {
    return bonusCredits;
  }

  public BigDecimal getAmountUsd() {
    return amountUsd;
  }

  public String getPaymentSource() {
    return paymentSource;
  }

  public String getPaymentTransactionId() {
    return paymentTransactionId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public int getResultingBalance() {
    return resultingBalance;
  }
}
