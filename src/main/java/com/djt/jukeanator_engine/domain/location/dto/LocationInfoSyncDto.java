package com.djt.jukeanator_engine.domain.location.dto;

import java.io.Serializable;

/**
 * A slave's own operator-editable location info (set through the JFC/Swing "Edit Location Info"
 * dialog), pushed to master over the {@code /ws-slave} STOMP connection on every (re)connect and
 * whenever it is edited -- see {@code SlaveConnectionManager}. Master needs the coordinates and
 * geo-fence flag to enforce the geo-fence for that location's Web/Mobile UI patrons.
 *
 * <p>Deliberately not {@code UpdateLocationInfoRequest}: its {@code boolean isGeoFenced}
 * component is serialized differently by different Jackson versions, and the slave and master
 * sides of this connection use different Jackson generations.
 */
public record LocationInfoSyncDto(String name, Double latitude, Double longitude,
    String logoName, Boolean geoFenced) implements Serializable {
}
