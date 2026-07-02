package com.djt.jukeanator_engine.domain.common.controller;
import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityDoesNotExistException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityDoesNotExistException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(EntityAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAlreadyExists(EntityAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(InvalidPrincipalException.class)
    public ResponseEntity<ApiError> handleInvalidPrincipal(InvalidPrincipalException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIO(IOException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntime(RuntimeException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, Exception ex) {
        ApiError error = new ApiError(
                ex.getMessage(),
                ex.getClass().getSimpleName(),
                status.value()
        );
        return ResponseEntity.status(status).body(error);
    }
}