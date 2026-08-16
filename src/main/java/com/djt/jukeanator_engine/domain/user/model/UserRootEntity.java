package com.djt.jukeanator_engine.domain.user.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * The whole user list is treated as a single aggregate -- one row here owns every
 * {@link UserEntity}, which is why this is a singleton (persistentIdentity is always 0) rather
 * than one row per user. This mirrors {@code UserRepositoryFileSystemImpl}, which likewise
 * (de)serializes every user as one unit; {@code UserRepositoryJpaImpl} just does the equivalent
 * with {@code entityManager.merge(root)} cascading to every {@link UserEntity}/
 * {@link PlaylistEntity} instead of writing one file.
 */
@Entity
@Table(name = "user_root")
public class UserRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String USER_LIST_FILENAME = "JukeANator_Users.json";

  // Field type must be the Map interface, not TreeMap: once this entity is loaded by JPA,
  // Hibernate substitutes its own managed Map implementation here. Callers only ever use Map
  // interface methods (values()/put()/get()), so this is a behavior-preserving change.
  @OneToMany(mappedBy = "userRoot", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @MapKey(name = "emailAddress")
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

    user.setUserRoot(this);
    return this.users.put(user.getEmailAddress(), user);
  }

  public UserEntity getUserByEmailAddressNullIfNotExists(String emailAddress) {

    return this.users.get(emailAddress);
  }

  public UserEntity removeUser(String emailAddress) {

    UserEntity removed = this.users.remove(emailAddress);
    if (removed != null) {
      removed.setUserRoot(null);
    }
    return removed;
  }
}

