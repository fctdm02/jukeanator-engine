package com.djt.jukeanator_engine.domain.common.exception;

public final class EntityAlreadyExistsException extends Exception {

  private static final long serialVersionUID = 1L;

  public EntityAlreadyExistsException(String message) {
    super(message);
  }
}