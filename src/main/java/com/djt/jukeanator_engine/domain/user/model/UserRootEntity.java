package com.djt.jukeanator_engine.domain.user.model;

import java.util.Collection;
import java.util.TreeMap;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

public class UserRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String USER_LIST_FILENAME = "JukeANator_Users.oos";

  private TreeMap<String, UserEntity> users = new TreeMap<>();

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
}

