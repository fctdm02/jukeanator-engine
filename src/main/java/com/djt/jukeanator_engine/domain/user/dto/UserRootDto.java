package com.djt.jukeanator_engine.domain.user.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code UserRootEntity}. This is the
 * top-level shape written to and read from {@code UserRootEntity.USER_LIST_FILENAME}.
 */
public class UserRootDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private List<UserDto> users = new ArrayList<>();

  public UserRootDto() {}

  public UserRootDto(List<UserDto> users) {
    this.users = users;
  }

  public List<UserDto> getUsers() {
    return users;
  }

  public void setUsers(List<UserDto> users) {
    this.users = users;
  }
}
