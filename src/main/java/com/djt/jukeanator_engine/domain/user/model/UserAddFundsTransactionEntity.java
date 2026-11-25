package com.djt.jukeanator_engine.domain.user.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only Add-Funds record: a patron spending real money (charged via
 * {@code PaymentGateway}, e.g. Braintree) to obtain song credits usable only at one location.
 * Owned by the patron's {@link UserLocationAccountEntity} at that location. Deliberately distinct
 * from {@link UserSongCreditUsageEntity}, which records the later, separate act of spending
 * already-owned credits to queue a song.
 *
 * <p>The full purchase amount is also recorded once in that location's financial ledger (see
 * {@code LocalMobileTransactionEntity}), exactly like a swipe on the location's own card reader.
 *
 * @author tmyers
 */
@Entity
@Table(name = "user_add_funds_transaction")
public class UserAddFundsTransactionEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  // Persistence-only back-reference -- UserLocationAccountEntity.addAddFundsTransaction() keeps
  // it in sync.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_location_account_id", nullable = false)
  private UserLocationAccountEntity locationAccount;

  // The location the credits were bought for (and are usable at), denormalized from the account
  // for reporting.
  @Column(name = "location_id", nullable = false)
  private Integer locationId;

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

  public UserAddFundsTransactionEntity(Integer persistentIdentity, Integer locationId,
      String packageId, int creditsAwarded, int bonusCredits, BigDecimal amountUsd,
      String paymentSource, String paymentTransactionId, Instant timestamp,
      int resultingBalance) {
    super(persistentIdentity);
    this.locationId = locationId;
    this.packageId = packageId;
    this.creditsAwarded = creditsAwarded;
    this.bonusCredits = bonusCredits;
    this.amountUsd = amountUsd;
    this.paymentSource = paymentSource;
    this.paymentTransactionId = paymentTransactionId;
    this.timestamp = timestamp;
    this.resultingBalance = resultingBalance;
  }

  void setLocationAccount(UserLocationAccountEntity locationAccount) {
    this.locationAccount = locationAccount;
  }

  @Override
  public String getNaturalIdentity() {
    return getUserEmail() + "/" + timestamp + "/" + getPersistentIdentity();
  }

  public String getUserEmail() {
    return locationAccount != null ? locationAccount.getUserEmail() : null;
  }

  public Integer getLocationId() {
    return locationId;
  }

  // Package-private: only UserLocationAccountEntity.changeLocationId re-tags a purchase.
  void changeLocationId(Integer oldLocationId, Integer newLocationId) {
    if (Objects.equals(oldLocationId, this.locationId)) {
      this.locationId = newLocationId;
    }
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
