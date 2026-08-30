package com.djt.jukeanator_engine.domain.location.dto;

/** This instance's own location — display name + logo for the web UI's header. */
public record OwnLocationDto(Integer locationId, String name, String logoName) {
}
