package com.djt.jukeanator_engine.domain.user.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * One append-only record of a user spending already-owned song credits to add a song to (or
 * otherwise act on) a location's queue via the mobile/web UI, owned by the {@link UserEntity} it
 * belongs to. Deliberately distinct from {@link UserAddFundsTransactionEntity}, which records the
 * earlier, separate act of a user obtaining those credits with real money -- see that class's
 * javadoc. Mobile/web-originated only -- {@code UserServiceImpl.deductCredits} explicitly skips
 * the local walk-up (JFC/Swing) user, so this table (renamed from {@code mobile_transactions};
 * see {@code V2__...} migration) never records local bill-acceptor/credit-card-reader activity --
 * see {@code LocalCashTransactionEntity}/{@code LocalCreditTransactionEntity} for those.
 * {@code locationId} is {@code null} for standalone-mode (non-location-attributed) spends, and for
 * the pre-multi-tenant call sites that don't yet have a location to tag — never retroactively
 * backfilled.
 *
 * @author tmyers
 */
@Entity
@Table(name = "user_song_credit_usage")
public class UserSongCreditUsageEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  // Persistence-only back-reference -- the FK column JPA needs to own the UserEntity <->
  // transaction relationship. UserEntity.addUserSongCreditUsage() is the single place that keeps
  // this back-reference in sync (see setUser()).
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @Column(name = "location_id")
  private Integer locationId;

  @Column(nullable = false)
  private int amount; // always negative -- a spend, never a purchase (see UserAddFundsTransactionEntity)

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserSongCreditUsageType type;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(name = "song_album_id")
  private Integer songAlbumId;

  @Column(name = "song_id")
  private Integer songId;

  @Column(name = "resulting_balance", nullable = false)
  private int resultingBalance;

  protected UserSongCreditUsageEntity() {} // for JPA

  public UserSongCreditUsageEntity(Integer persistentIdentity, Integer locationId, int amount,
      UserSongCreditUsageType type, Instant timestamp, Integer songAlbumId, Integer songId,
      int resultingBalance) {
    super(persistentIdentity);
    this.locationId = locationId;
    this.amount = amount;
    this.type = type;
    this.timestamp = timestamp;
    this.songAlbumId = songAlbumId;
    this.songId = songId;
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

  public Integer getLocationId() {
    return locationId;
  }

  public int getAmount() {
    return amount;
  }

  public UserSongCreditUsageType getType() {
    return type;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public Integer getSongAlbumId() {
    return songAlbumId;
  }

  public Integer getSongId() {
    return songId;
  }

  public int getResultingBalance() {
    return resultingBalance;
  }
}
