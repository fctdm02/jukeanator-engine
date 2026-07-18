package com.djt.jukeanator_engine.domain.location.dto;

/** A slave's reply to a {@link CommandEnvelope}, correlated back to the waiting master request.
 * See {@link CommandEnvelope} for why {@code payload} is {@code Object}, not {@code JsonNode}. */
public record CommandReplyDto(String correlationId, boolean success, Object payload,
    String errorMessage) {
}
