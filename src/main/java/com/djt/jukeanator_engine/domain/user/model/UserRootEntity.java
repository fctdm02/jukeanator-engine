package com.djt.jukeanator_engine.domain.user.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * The whole user list is treated as a single in-memory aggregate -- one instance here owns every
 * {@link UserEntity}, which is why this is a singleton (persistentIdentity is always 0) rather
 * than one instance per user. This mirrors {@code UserRepositoryFileSystemImpl}, which likewise
 * (de)serializes every user as one unit. It is not itself JPA-mapped: there is no {@code
 * user_root} table -- {@code UserRepositoryJpaImpl} loads every {@link UserEntity} row directly
 * and assembles this aggregate around them in memory, since a relational schema has no need for a
 * singleton "root" row to own a one-table collection.
 */
public class UserRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(UserRootEntity.class);

  public static final String USER_LIST_FILENAME = "JukeANator_Users.json";

  // Keyed without regard to case: an email address names one account however it is typed (a
  // phone's keyboard often capitalizes the first letter).
  private Map<String, UserEntity> users = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public UserRootEntity() {
    super(Integer.valueOf(0));
  }

  @Override
  public String getNaturalIdentity() {
    return "UserRootEntity";
  }

  public Collection<UserEntity> getUsers() {

    return this.users.values();
  }

  public UserEntity addUser(UserEntity user) {

    UserEntity replaced = this.users.put(user.getEmailAddress(), user);
    if (replaced != null && replaced != user) {
      // Only possible for accounts stored before addresses were compared without regard to case.
      log.warn("Two accounts share email address [{}] (ignoring case): user ids {} and {} -- only "
          + "the latter can sign in", user.getEmailAddress(), replaced.getPersistentIdentity(),
          user.getPersistentIdentity());
    }
    return replaced;
  }

  /** {@code emailAddress} is matched without regard to case; null matches no one. */

  /**
   * The placeholder id for the next new user -- one past the highest id any user currently holds,
   * never a user count, for the reason given on {@code UserEntity.nextIdentity}: a count can equal
   * an existing user's real JPA id, and UserRepositoryJpaImpl would then merge() the new user over
   * that existing account.
   */
  public Integer nextUserPersistentIdentity() {
    return UserEntity.nextIdentity(this.users.values(), 1);
  }

  public UserEntity getUserByEmailAddressNullIfNotExists(String emailAddress) {

    return emailAddress == null ? null : this.users.get(emailAddress);
  }

  public UserEntity removeUser(String emailAddress) {

    return this.users.remove(emailAddress);
  }

  /**
   * Closes {@code emailAddress}'s account via {@link UserEntity#anonymize} and re-keys it under its
   * closed-account email address, so the original address can register again while the account's
   * financial records stay in the user root. Returns the closed account, or {@code null} if no user
   * has {@code emailAddress}.
   */
  public UserEntity closeUser(String emailAddress, String closedEmailAddress,
      String unusablePasswordHash) {

    UserEntity user = this.users.remove(emailAddress);
    if (user == null) {
      return null;
    }
    user.anonymize(closedEmailAddress, unusablePasswordHash);
    this.users.put(user.getEmailAddress(), user);
    return user;
  }

  /**
   * Re-tags every user's location-tagged state from {@code oldLocationId} to {@code newLocationId}
   * -- the in-memory counterpart of {@code LocationRepositoryJpaImpl.changeLocationId}'s user
   * table updates.
   */
  public void changeLocationId(Integer oldLocationId, Integer newLocationId) {

    for (UserEntity user : this.users.values()) {
      user.changeLocationId(oldLocationId, newLocationId);
    }
  }
}

