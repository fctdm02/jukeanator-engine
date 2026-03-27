package com.djt.jukeanator_engine.domain.common.exception;

public final class EntityDoesNotExistException extends Exception {

  private static final long serialVersionUID = 1L;

  public EntityDoesNotExistException(String message) {
    super(message);
  }
}