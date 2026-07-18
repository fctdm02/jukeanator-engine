package com.djt.jukeanator_engine.domain.location.dto;

/**
 * A command master sends down to a specific slave over its {@code /ws-slave} session.
 * {@code commandType} identifies which {@code SongQueueService}/{@code SongPlayerService} method
 * to invoke; {@code payload} is that method's argument, deserialized by the slave based on
 * {@code commandType} (see {@code SlaveConnectionManager}).
 *
 * <p>
 * {@code payload} is deliberately {@code Object}, not {@code JsonNode} — the concrete type is
 * erased over the wire either way (there's no room for type tags), but {@code JsonNode} requires a
 * Jackson message converter that supports deserializing into an abstract polymorphic type, which
 * isn't guaranteed to be Spring's STOMP message-converter default. Landing as a generic
 * {@code LinkedHashMap}/{@code List}/primitive and re-hydrating via
 * {@code ObjectMapper.convertValue(...)} works with any converter.
 */
public record CommandEnvelope(String correlationId, String commandType, Object payload) {
}
