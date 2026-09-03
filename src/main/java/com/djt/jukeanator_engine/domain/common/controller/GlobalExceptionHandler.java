package com.djt.jukeanator_engine.domain.common.controller;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.InvalidPrincipalException;
import com.djt.jukeanator_engine.domain.location.exception.LocationOfflineException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(EntityDoesNotExistException.class)
    public void handleNotFound(EntityDoesNotExistException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logExpected(HttpStatus.NOT_FOUND, ex, request);
        write(HttpStatus.NOT_FOUND, ex, response);
    }

    @ExceptionHandler(EntityAlreadyExistsException.class)
    public void handleAlreadyExists(EntityAlreadyExistsException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logExpected(HttpStatus.CONFLICT, ex, request);
        write(HttpStatus.CONFLICT, ex, response);
    }

    @ExceptionHandler(InvalidPrincipalException.class)
    public void handleInvalidPrincipal(InvalidPrincipalException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logExpected(HttpStatus.UNAUTHORIZED, ex, request);
        write(HttpStatus.UNAUTHORIZED, ex, response);
    }

    @ExceptionHandler(LocationOfflineException.class)
    public void handleLocationOffline(LocationOfflineException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logExpected(HttpStatus.SERVICE_UNAVAILABLE, ex, request);
        write(HttpStatus.SERVICE_UNAVAILABLE, ex, response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public void handleBadRequest(IllegalArgumentException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logExpected(HttpStatus.BAD_REQUEST, ex, request);
        write(HttpStatus.BAD_REQUEST, ex, response);
    }

    // These three catch whatever wasn't anticipated by a specific handler above -- including
    // framework exceptions the Spring MVC layer itself raises and already knows the right status
    // for, e.g. NoResourceFoundException (404, hit whenever a request matches no controller and no
    // static resource, such as /api/locations on a standalone/slave instance where that endpoint is
    // intentionally absent). Note NoResourceFoundException is a *checked* ServletException, not a
    // RuntimeException, so it's handleGeneric() -- not handleRuntime() -- that actually receives it;
    // resolveAndHandle() below is shared by all three so the status/logging logic doesn't depend on
    // which one a given exception happens to land in.
    @ExceptionHandler(IOException.class)
    public void handleIO(IOException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        resolveAndHandle(ex, request, response);
    }

    @ExceptionHandler(RuntimeException.class)
    public void handleRuntime(RuntimeException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        resolveAndHandle(ex, request, response);
    }

    @ExceptionHandler(Exception.class)
    public void handleGeneric(Exception ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        resolveAndHandle(ex, request, response);
    }

    // Spring's own framework exceptions (NoResourceFoundException, HttpRequestMethodNotSupportedException,
    // etc.) implement ErrorResponse and already know their correct HTTP status -- e.g. 404 for a
    // route/resource that was never expected to exist in this app.mode. Anything else (a genuine bug)
    // falls back to 500. Only the latter case is worth an ERROR-level stack trace in production.
    private void resolveAndHandle(Exception ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus resolved = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (resolved != null) {
                status = resolved;
            }
        }
        if (status.is5xxServerError()) {
            logUnexpected(ex, request);
        } else {
            logExpected(status, ex, request);
        }
        write(status, ex, response);
    }

    // Expected, business-level outcomes (404/409/401/503/400) — one line, no stack trace, so
    // routine client errors don't drown out real problems in production logs.
    private void logExpected(HttpStatus status, Exception ex, HttpServletRequest request) {
        log.warn("[{}] {} {} — {}: {}", status.value(), request.getMethod(),
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());
    }

    // Anything reaching here was not anticipated by a specific handler above — full stack trace
    // is what makes these debuggable once this is running unattended in production.
    private void logUnexpected(Exception ex, HttpServletRequest request) {
        log.error("[500] {} {} — unhandled {}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName(), ex);
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
