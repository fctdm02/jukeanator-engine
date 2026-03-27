package com.djt.jukeanator_engine.domain.common.exception;

import java.util.ArrayList;
import java.util.List;

public final class ValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final List<String> validationErrors = new ArrayList<>();

  public ValidationException(String message, List<String> validationErrors) {
    super(message);
    if (validationErrors != null) {
      this.validationErrors.addAll(validationErrors);
    }
  }
  
  public List<String> getValidationErrors() {
    return this.validationErrors;
  }

  @Override
  public String getMessage() {

    return new StringBuilder()
        .append(super.getMessage())
        .append(", validationErrors: ")
        .append(validationErrors)
        .toString();
  }
}
