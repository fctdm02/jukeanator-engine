package com.djt.jukeanator_engine.domain.user.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.user.dto.UserDto;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;

/**
 * @author tmyers
 */
public final class UserMapper {

  public static List<UserDto> toDto(List<UserEntity> entities) {

    List<UserDto> dtos = new ArrayList<>();

    for (UserEntity entity : entities) {

      UserDto dto = toDto(entity);

      dtos.add(dto);
    }

    return dtos;
  }

  public static UserDto toDto(UserEntity entity) {

    UserDto dto = new UserDto(entity.getFirstName(), entity.getLastName(), entity.getEmailAddress(),
        entity.getPasswordHash(), entity.getNumCredits(), entity.getSongPlayHistory(),
        entity.getRole());
    dto.setSearchHistory(entity.getSearchHistory());

    return dto;
  }
}
