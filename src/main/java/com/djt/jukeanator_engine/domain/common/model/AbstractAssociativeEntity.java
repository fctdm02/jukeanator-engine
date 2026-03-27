package com.djt.jukeanator_engine.domain.common.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.model.validation.ValidationMessage;

public abstract class AbstractAssociativeEntity extends AbstractEntity {
  private static final long serialVersionUID = 1L;

  // Used to inform the service/repository that this entity needs to be persisted.
  private boolean needsPersisting;

  public AbstractAssociativeEntity() {
    super();
  }

  public AbstractAssociativeEntity(boolean needsPersisting) {
    super();
    this.needsPersisting = needsPersisting;
  }

  public boolean getNeedsPersisting() {
    return needsPersisting;
  }

  public void resetNeedsPersisting() {
    needsPersisting = false;
  }

  @Override
  public void setIsDeleted() {

    if (needsPersisting) {
      throw new IllegalStateException(
          "Cannot set: [" + this + "] isDeleted if needsPersisting is already set.");
    }
    super.setIsDeleted();
  }

  @Override
  public void setIsModified(String modifiedAttributeName) {

    if (needsPersisting) {
      return;
    }
    super.setIsModified(modifiedAttributeName);
  }

  public void setNeedsPersisting(boolean needsPersisting) {

    if (getIsDeleted()) {
      throw new IllegalStateException(
          "Cannot set: [" + this + "] needsPersisting if isDeleted is already set.");
    }
    if (getIsModified()) {
      setNotModified();
    }
    this.needsPersisting = needsPersisting;
  }

  public abstract Map<String, Integer> getParentIdentities();

  protected <T> boolean addChild(Set<T> set, T t, AbstractEntity parent)
      throws EntityAlreadyExistsException {

    if (set.contains(t)) {
      throw new EntityAlreadyExistsException(parent.getClassAndNaturalIdentity() + " already has "
          + t.getClass().getSimpleName() + ": [" + t + "].");
    }
    return set.add(t);
  }

  public int hashCode() {

    return getParentIdentities().hashCode();
  }

  public boolean equals(Object that) {

    if (that == null) {
      return false;
    }

    if (that == this) {
      return true;
    }

    if (!this.getClass().equals(that.getClass())) {
      return false;
    }

    return this.getParentIdentities()
        .equals(((AbstractAssociativeEntity) that).getParentIdentities());
  }

  public String getNaturalIdentity() {

    return getParentIdentities().toString();
  }

  @Override
  public void validate(List<ValidationMessage> validationMessages) {

    Map<String, Integer> parentIdentities = getParentIdentities();
    if (parentIdentities == null || parentIdentities.size() < 2) {

      // TODO: TDM
      /*
       * validationMessages.add(ValidationMessage.builder()
       * .withIssueType(IssueType.INVALID_ASSOCIATIVE_ENTITY_INSTANTIATION)
       * .withDetails("Associative entities must contain at least 2 parent identities")
       * .withEntityType(this.getClass().getSimpleName())
       * .withNaturalIdentity(this.getNaturalIdentity())
       * .withRemediationDescription("Instantiate with at least 2 parent identities") .build());
       */
    }
  }
}
