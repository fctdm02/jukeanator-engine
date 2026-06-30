package com.djt.jukeanator_engine.domain.user.service;

import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import java.util.List;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
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

  List<CreditPackageDto> getCreditPackages();
  void changePassword(String emailAddress, ChangePasswordRequest request);
  void deleteAccount(String emailAddress);
  void addFunds(String emailAddress, AddFundsRequest request);
  UserProfileDto updateProfile(String emailAddress, UpdateProfileRequest request);

  List<String> getSearchHistory(String emailAddress);

  void addSearchHistory(String emailAddress, String query);

  void removeSearchHistory(String emailAddress, int index);

  /**
   *
   * @param event
   */
  @PublicServiceMethod
  void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event);
}
