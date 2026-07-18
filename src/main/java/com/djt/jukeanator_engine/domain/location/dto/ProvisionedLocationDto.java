package com.djt.jukeanator_engine.domain.location.dto;

/**
 * Returned exactly once, at provisioning time. {@code apiKey} is the plaintext secret the slave
 * must configure as {@code app.location-api-key} — only its bcrypt hash is ever persisted, so it
 * cannot be recovered or shown again after this response.
 */
public record ProvisionedLocationDto(String locationId, String apiKey, String name) {
}
