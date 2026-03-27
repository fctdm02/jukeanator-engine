package com.djt.jukeanator_engine.domain.common.model;

import java.util.Optional;
import java.util.Set;

import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

public abstract class AbstractPersistentEntity extends AbstractEntity {
  private static final long serialVersionUID = 1L;
  
  private Integer persistentIdentity;
  
  
  public AbstractPersistentEntity() {}

  public AbstractPersistentEntity(Integer persistentIdentity) {
    super();
    this.persistentIdentity = persistentIdentity;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }
  
  // Used by the JDBC implementations to know when to INSERT vs UPDATE
  public void setPersistentIdentity(Integer persistentIdentity) {
    this.persistentIdentity = persistentIdentity;
  }

  public String getClassAndPersistentIdentity() {

    return new StringBuilder()
        .append(getClass().getSimpleName())
        .append("(")
        .append(persistentIdentity)
        .append(")")
        .toString();
  }

  @Override
  public int hashCode() {

    if (this.getPersistentIdentity() != null) {
      return getPersistentIdentity().hashCode();
    }

    return super.hashCode();
  }

  @Override
  public boolean equals(Object that) {

    if (that == null) {
      return false;
    }

    if (that == this) {
      return true;
    }

    if (this.getClass() != that.getClass()) {
      return false;
    }

    if (this.persistentIdentity != null
        && ((AbstractPersistentEntity) that).persistentIdentity != null) {

      return this.persistentIdentity.equals(((AbstractPersistentEntity) that).persistentIdentity);
    }

    return this.getNaturalIdentity().equals(((AbstractEntity) that).getNaturalIdentity());
  }

  public boolean equals(AbstractPersistentEntity that) {

    if (that == null) {
      return false;
    }

    if (that == this) {
      return true;
    }
    
    if (this.persistentIdentity != null && that.persistentIdentity != null) {
      return this.persistentIdentity.equals(that.persistentIdentity);
    }

    return this.getNaturalIdentity().equals(that.getNaturalIdentity());
  }
  
  protected <T extends AbstractPersistentEntity> boolean addChildIfNotExists(
      Set<T> set, 
      T t,
      AbstractPersistentEntity parent) {
    
    if (set.contains(t)) {
      return false;
    }
    t.setIsModified(t.getClass().getSimpleName());
    return set.add(t);
  }  
  
  protected <T extends AbstractPersistentEntity> boolean addChild(
      Set<T> set, 
      T t,
      AbstractPersistentEntity parent) throws EntityAlreadyExistsException {
    
    if (set.contains(t)) {
      String pcn = parent.getClassAndNaturalIdentity();
      String tcn = t.getClassAndNaturalIdentity();
      throw new EntityAlreadyExistsException( 
          pcn
          + " already has child" 
          + tcn);
    }
    parent.setIsModified("added_child: " + t.getClass().getSimpleName());
    return set.add(t);
  }  

  protected <T extends AbstractPersistentEntity> boolean removeChild(
      Set<T> set, 
      T t) throws EntityDoesNotExistException {
    
    if (!set.contains(t)) {
      String pcn = this.getClassAndNaturalIdentity();
      String tcn = t.getClassAndNaturalIdentity();
      throw new EntityDoesNotExistException(
          pcn
          + " does not have child" 
          + tcn);
    }
    t.setIsModified(t.getClass().getSimpleName());
    return set.remove(t);
  }  
  
  protected <T extends AbstractPersistentEntity> Optional<T> getChildEmptyIfINotExists(
      Class<T> childClass,
      Set<T> set,
      Integer persistentIdentity,
      AbstractPersistentEntity parent) {

    return Optional.ofNullable(set
        .stream()
        .filter(t -> t.getPersistentIdentity().equals(persistentIdentity))
        .findAny()
        .orElse(null));
  }
  
  protected <T extends AbstractPersistentEntity> T getChild(
      Class<T> childClass,
      Set<T> set,
      Integer persistentIdentity,
      AbstractPersistentEntity parent) throws EntityDoesNotExistException {

    return set
        .stream()
        .filter(t -> t.getPersistentIdentity().equals(persistentIdentity))
        .findAny()
        .orElseThrow(() -> new EntityDoesNotExistException(
            childClass.getSimpleName()
            + " with id: [" 
            + persistentIdentity 
            + "] not found in "
            + parent.getClassAndPersistentIdentity()
            ));
  }
}
