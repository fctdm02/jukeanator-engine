package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code UserRootEntity}. This is the
 * top-level shape written to and read from {@code UserRootEntity.USER_LIST_FILENAME}.
 */
public record UserRootDto(List<UserDto> users) implements Serializable {
}
