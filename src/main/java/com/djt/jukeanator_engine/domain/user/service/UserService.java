package com.djt.jukeanator_engine.domain.user.service;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserProfileDto;

/**
 * @author tmyers
 */
public interface UserService {

  /**
   * 
   * @param request
   * @return
   */
  @PublicServiceMethod
  AuthResponse register(RegisterRequest request);

  /**
   * 
   * @param request
   * @return
   */
  @PublicServiceMethod
  AuthResponse login(LoginRequest request);

  /**
   * 
   * @param emailAddress
   * @return
   */
  UserProfileDto getProfile(String emailAddress);

  /**
   * 
   * @param event
   */
  @PublicServiceMethod
  void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event);
}
