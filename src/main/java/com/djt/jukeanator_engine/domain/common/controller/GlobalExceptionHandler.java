package com.djt.jukeanator_engine.domain.common.controller;
import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(EntityDoesNotExistException.class)
    public void handleNotFound(EntityDoesNotExistException ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.NOT_FOUND, ex, response);
    }

    @ExceptionHandler(EntityAlreadyExistsException.class)
    public void handleAlreadyExists(EntityAlreadyExistsException ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.CONFLICT, ex, response);
    }

    @ExceptionHandler(InvalidPrincipalException.class)
    public void handleInvalidPrincipal(InvalidPrincipalException ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.UNAUTHORIZED, ex, response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public void handleBadRequest(IllegalArgumentException ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.BAD_REQUEST, ex, response);
    }

    @ExceptionHandler(IOException.class)
    public void handleIO(IOException ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.INTERNAL_SERVER_ERROR, ex, response);
    }

    @ExceptionHandler(RuntimeException.class)
    public void handleRuntime(RuntimeException ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.INTERNAL_SERVER_ERROR, ex, response);
    }

    @ExceptionHandler(Exception.class)
    public void handleGeneric(Exception ex, HttpServletResponse response) throws IOException {
        write(HttpStatus.INTERNAL_SERVER_ERROR, ex, response);
    }

    // Write straight to the servlet response instead of returning a ResponseEntity. Some errors
    // (e.g. those surfacing through Tomcat's async error dispatch) reach us after the response
    // already has a Content-Type set from earlier processing - e.g. internet scanners hitting the
    // now-public-facing port with "Accept: application/javascript". Spring's message converters
    // treat that as a fixed "preset" type and refuse to write Jackson/ApiError into it even if the
    // ResponseEntity requests application/json, throwing HttpMessageNotWritableException and
    // masking the real error. Resetting the response before writing clears any such stale header.
    private void write(HttpStatus status, Exception ex, HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.reset();
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                ex.getMessage(),
                ex.getClass().getSimpleName(),
                status.value()
        );
        objectMapper.writeValue(response.getWriter(), error);
    }
}
