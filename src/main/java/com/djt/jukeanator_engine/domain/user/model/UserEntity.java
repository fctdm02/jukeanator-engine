package com.djt.jukeanator_engine.domain.user.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.common.security.UserRole;

/**
 * A patron (or admin) account. Everything a mobile/web patron owns that is only meaningful at one
 * location -- credits, song-play history, playlists and the credit ledgers -- lives on a
 * {@link UserLocationAccountEntity}, one per location the patron has used, never on the user
 * directly. Only identity, credentials and search history are user-wide.
 */
@Entity
@Table(name = "user_account")
public class UserEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(UserEntity.class);

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "email_address", nullable = false, unique = true)
  private String emailAddress;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  // fetch = EAGER throughout this aggregate because UserServiceImpl loads the whole user root
  // once and holds it in memory indefinitely, well outside the scope of any one transaction --
  // the same "fully materialized" shape that UserRepositoryFileSystemImpl already assumes when it
  // deserializes the entire file in one shot.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_search_history", joinColumns = @JoinColumn(name = "user_id"))
  @OrderColumn(name = "search_order")
  @Column(name = "search_query", length = 500)
  private List<String> searchHistory = new ArrayList<>();

  // One account per location the patron has used. A List (not an id-hashed Set) so a placeholder
  // id replaced in place on persist() needs no rehash.
  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("persistentIdentity ASC")
  private List<UserLocationAccountEntity> locationAccounts = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role = UserRole.ROLE_USER;

  // PENDING from Web/Mobile UI registration until the emailed verification link is opened; every
  // other account (admins, the kiosk's internal user, and any account stored before this field
  // existed) is ACTIVE.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status = UserStatus.ACTIVE;

  // SHA-256 (hex) of the outstanding verification token -- the raw token exists only in the email.
  @Column(name = "verification_token_hash", length = 64)
  private String verificationTokenHash;

  @Column(name = "verification_token_expires_at")
  private Instant verificationTokenExpiresAt;

  protected UserEntity() {} // for JPA

  public UserEntity(Integer persistentIdentity, String firstName, String lastName,
      String emailAddress, String passwordHash, UserRole role) {
    super(persistentIdentity);
    this.firstName = firstName;
    this.lastName = lastName;
    this.emailAddress = emailAddress;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  // --- Per-location accounts ----------------------------------------------------------------

  public List<UserLocationAccountEntity> getLocationAccounts() {
    if (locationAccounts == null) {
      locationAccounts = new ArrayList<>();
    }
    return locationAccounts;
  }

  /** This user's account at {@code locationId}, or null if they have never used that location. */
  public UserLocationAccountEntity getLocationAccountNullIfNotExists(Integer locationId) {

    if (locationId == null) {
      return null;
    }
    for (UserLocationAccountEntity account : getLocationAccounts()) {
      if (locationId.equals(account.getLocationId())) {
        return account;
      }
    }
    return null;
  }

  /**
   * This user's account at {@code locationId}, created (with an empty My Favorites playlist and a
   * zero balance) on first use. The caller must store the user if one was created.
   */
  public UserLocationAccountEntity getOrCreateLocationAccount(Integer locationId) {

    Objects.requireNonNull(locationId, "locationId cannot be null");
    UserLocationAccountEntity account = getLocationAccountNullIfNotExists(locationId);
    if (account != null) {
      return account;
    }
    account = new UserLocationAccountEntity(nextIdentity(getLocationAccounts(), 1), locationId);
    account.setUser(this);
    getLocationAccounts().add(account);
    return account;
  }

  /** The balance at {@code locationId}; 0 where the user has no account there yet. */
  public int getNumCredits(Integer locationId) {
    UserLocationAccountEntity account = getLocationAccountNullIfNotExists(locationId);
    return account == null ? 0 : account.getNumCredits();
  }

  /** Re-attaches an already-persisted account (rehydration only). */
  public UserLocationAccountEntity restoreLocationAccount(UserLocationAccountEntity account) {
    account.setUser(this);
    getLocationAccounts().add(account);
    return account;
  }

  // Package-private: only UserRootEntity.changeLocationId re-tags a user, when this instance's own
  // location id is corrected post-handshake.
  void changeLocationId(Integer oldLocationId, Integer newLocationId) {

    if (getLocationAccountNullIfNotExists(newLocationId) != null) {
      log.warn("User [{}] already has an account at location {}; not re-tagging the account at {}",
          emailAddress, newLocationId, oldLocationId);
      return;
    }
    for (UserLocationAccountEntity account : getLocationAccounts()) {
      account.changeLocationId(oldLocationId, newLocationId);
    }
  }

  // --- Search history (user-wide) -----------------------------------------------------------

  public List<String> getSearchHistory() {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    return searchHistory;
  }

  public void setSearchHistory(List<String> searchHistory) {
    this.searchHistory = searchHistory;
  }

  public void addToSearchHistory(String query, int maxSize) {
    if (searchHistory == null)
      searchHistory = new ArrayList<>();
    searchHistory.remove(query);
    searchHistory.add(0, query);
    if (searchHistory.size() > maxSize) {
      searchHistory = new ArrayList<>(searchHistory.subList(0, maxSize));
    }
  }

  public void removeFromSearchHistory(int index) {
    if (searchHistory == null)
      return;
    if (index >= 0 && index < searchHistory.size()) {
      searchHistory.remove(index);
    }
  }

  // --- Placeholder identities and persistence support ---------------------------------------

  /**
   * One past the highest id currently held in {@code children} ({@code firstId} if there are
   * none). Every new child's placeholder id is minted this way rather than from the collection's
   * size: under JPA, persisted children carry real ids from the shared persistent_identity_seq, and
   * a size-derived placeholder can equal one of them -- UserRepositoryJpaImpl would then treat the
   * new child as that existing row (merge() over it), and a HashSet keyed by id would silently drop
   * it. One past the highest id can never equal an existing one, and under the filesystem
   * repository it also stays unique after deletions, which a size-derived id does not.
   */
  static Integer nextIdentity(Collection<? extends AbstractPersistentEntity> children,
      int firstId) {
    return Integer.valueOf(children.stream()
        .map(AbstractPersistentEntity::getPersistentIdentity)
        .filter(Objects::nonNull)
        .mapToInt(Integer::intValue)
        .max()
        .orElse(firstId - 1) + 1);
  }

  // A child equal to one already held (i.e. the same persistentIdentity) would otherwise be
  // silently dropped by the HashSet -- fail loudly instead.
  static void requireAdded(boolean added, AbstractPersistentEntity child) {
    if (!added) {
      throw new IllegalStateException("Duplicate persistentIdentity " + child.getPersistentIdentity()
          + " for new " + child.getClass().getSimpleName());
    }
  }

  /**
   * Re-buckets the id-hashed child sets after their members' persistentIdentity changed in place
   * (UserRepositoryJpaImpl replacing placeholder ids with real ones on persist()) -- otherwise
   * contains()/remove() can no longer find them. Clears and refills the same set instance rather
   * than replacing it, so JPA's orphan-removal bookkeeping on the collection is unaffected.
   */
  public void rehashChildCollections() {
    for (UserLocationAccountEntity account : getLocationAccounts()) {
      account.rehashChildCollections();
    }
  }

  static <T> void rehash(Set<T> set) {
    List<T> members = new ArrayList<>(set);
    set.clear();
    set.addAll(members);
  }

  /**
   * Swaps the lists JPA wrapped in its own collection types (on load, or on {@code persist()}) for
   * plain copies holding the same elements. This user is held detached for the app's lifetime, and
   * such a collection keeps a snapshot of the rows it held when it was wrapped -- {@code merge()}
   * trusts that stale snapshot over the database: a playlist added since is never seen as removed
   * (its row is never deleted), and a deleted one is looked up again on every later merge (failing
   * with {@code EntityNotFoundException}). Merging plain lists makes Hibernate compare against the
   * rows it loads in the same transaction instead. Called by {@code UserRepositoryJpaImpl} before
   * each merge. The credit-usage and Add Funds sets are left alone: they are append-only, and are
   * rehashed in place (see {@link #rehashChildCollections}).
   */
  public void replaceJpaCollectionsWithPlainCopies() {

    this.locationAccounts = new ArrayList<>(getLocationAccounts());
    for (UserLocationAccountEntity account : this.locationAccounts) {
      account.replaceJpaCollectionsWithPlainCopies();
    }
    this.searchHistory = new ArrayList<>(getSearchHistory());
  }

  /** The email-address domain given to every closed (anonymized) account. */
  public static final String CLOSED_ACCOUNT_EMAIL_DOMAIN = "@closed-account.invalid";

  /**
   * Closes this account in place: everything that identifies the person, plus their search
   * history and every location account's play history and playlists, is removed, while each
   * location account's balance, song-credit usages and Add Funds transactions -- financial records
   * a location's revenue and Braintree's charges are reconciled against -- are kept.
   * {@code closedEmailAddress} must end in {@link #CLOSED_ACCOUNT_EMAIL_DOMAIN}, and
   * {@code unusablePasswordHash} must match no password, so the account can never log in again.
   */
  public void anonymize(String closedEmailAddress, String unusablePasswordHash) {

    if (closedEmailAddress == null || !closedEmailAddress.endsWith(CLOSED_ACCOUNT_EMAIL_DOMAIN)) {
      throw new IllegalArgumentException(
          "A closed account's email address must end in " + CLOSED_ACCOUNT_EMAIL_DOMAIN);
    }

    this.firstName = "Closed";
    this.lastName = "Account";
    this.emailAddress = closedEmailAddress;
    this.passwordHash = unusablePasswordHash;
    // Fresh collections rather than clear() -- see replaceJpaCollectionsWithPlainCopies().
    this.searchHistory = new ArrayList<>();
    for (UserLocationAccountEntity account : getLocationAccounts()) {
      account.anonymize();
    }
    this.verificationTokenHash = null;
    this.verificationTokenExpiresAt = null;
  }

  public boolean isClosed() {
    return this.emailAddress != null && this.emailAddress.endsWith(CLOSED_ACCOUNT_EMAIL_DOMAIN);
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  /** Never null: an account stored before this field existed reads as ACTIVE. */
  public UserStatus getStatus() {
    return status == null ? UserStatus.ACTIVE : status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public boolean isPending() {
    return getStatus() == UserStatus.PENDING;
  }

  public String getVerificationTokenHash() {
    return verificationTokenHash;
  }

  public void setVerificationTokenHash(String verificationTokenHash) {
    this.verificationTokenHash = verificationTokenHash;
  }

  public Instant getVerificationTokenExpiresAt() {
    return verificationTokenExpiresAt;
  }

  public void setVerificationTokenExpiresAt(Instant verificationTokenExpiresAt) {
    this.verificationTokenExpiresAt = verificationTokenExpiresAt;
  }

  /** Replaces any outstanding verification token; the account stays (or becomes) PENDING. */
  public void issueVerificationToken(String tokenHash, Instant expiresAt) {
    this.status = UserStatus.PENDING;
    this.verificationTokenHash = Objects.requireNonNull(tokenHash);
    this.verificationTokenExpiresAt = Objects.requireNonNull(expiresAt);
  }

  public void markVerified() {
    this.status = UserStatus.ACTIVE;
    this.verificationTokenHash = null;
    this.verificationTokenExpiresAt = null;
  }

  @Override
  public String getNaturalIdentity() {
    return this.emailAddress;
  }
}
