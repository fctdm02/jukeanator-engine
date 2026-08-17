package com.djt.jukeanator_engine.domain.user.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
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

  public static final String USER_LIST_FILENAME = "JukeANator_Users.json";

  private Map<String, UserEntity> users = new TreeMap<>();

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

    return this.users.put(user.getEmailAddress(), user);
  }

  public UserEntity getUserByEmailAddressNullIfNotExists(String emailAddress) {

    return this.users.get(emailAddress);
  }

  public UserEntity removeUser(String emailAddress) {

    return this.users.remove(emailAddress);
  }
}

