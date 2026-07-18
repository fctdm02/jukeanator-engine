package com.djt.jukeanator_engine.domain.location.dto;

/**
 * A real-time event a slave pushes up to master over its {@code /ws-slave} session — song
 * started/finished, queue changed, a local walk-up song added, etc. Master republishes this to
 * {@code /topic/location/{locationId}/{eventType}} for whichever web/mobile clients are watching
 * that location, mirroring {@code WebSocketEventBroadcaster}'s existing per-topic shape. See
 * {@link CommandEnvelope} for why {@code payload} is {@code Object}, not {@code JsonNode}.
 */
public record LocationEventMessage(String eventType, Object payload) {
}
