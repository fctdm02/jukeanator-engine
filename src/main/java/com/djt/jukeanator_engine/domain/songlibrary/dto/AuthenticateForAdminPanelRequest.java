package com.djt.jukeanator_engine.domain.songlibrary.dto;

public record AuthenticateForAdminPanelRequest(String username, String password) {

  @Override
  public String toString() {
    return "AuthenticateForAdminPanelRequest [username=" + username + "]";
  }
}
