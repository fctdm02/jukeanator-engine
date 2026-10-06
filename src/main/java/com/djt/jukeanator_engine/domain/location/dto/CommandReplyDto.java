package com.djt.jukeanator_engine.domain.location.dto;

/** A slave's reply to a {@link CommandEnvelope}, correlated back to the waiting master request.
 * See {@link CommandEnvelope} for why {@code payload} is {@code Object}, not {@code JsonNode}.
 *
 * <p>{@code errorType} is the simple class name of the exception a failed command threw, so master
 * can re-raise the ones a patron must see as-is (e.g. a song the queue refuses) rather than a
 * generic failure. Null on success, and from a slave predating it. */
public record CommandReplyDto(String correlationId, boolean success, Object payload,
    String errorMessage, String errorType) {

  public CommandReplyDto(String correlationId, boolean success, Object payload,
      String errorMessage) {
    this(correlationId, success, payload, errorMessage, null);
  }
}
