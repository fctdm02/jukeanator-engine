package com.djt.jukeanator_engine.domain.user.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.user.dto.AddFundsRequest;
import com.djt.jukeanator_engine.domain.user.dto.AuthResponse;
import com.djt.jukeanator_engine.domain.user.dto.ChangePasswordRequest;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.dto.HomePageDto;
import com.djt.jukeanator_engine.domain.user.dto.LoginRequest;
import com.djt.jukeanator_engine.domain.user.dto.RegisterRequest;
import com.djt.jukeanator_engine.domain.user.dto.UpdateProfileRequest;
import com.djt.jukeanator_engine.domain.user.dto.UserHomePageDto;
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
   * @return
   */
  HomePageDto getPublicHomePage();

  /**
   * 
   * @param emailAddress
   * @return
   */
  UserHomePageDto getHomePage(String emailAddress);

  /**
   * 
   * @return
   */
  List<CreditPackageDto> getCreditPackages();

  /**
   * 
   * @param emailAddress
   * @param request
   */
  void changePassword(String emailAddress, ChangePasswordRequest request);

  /**
   * 
   * @param emailAddress
   */
  void deleteAccount(String emailAddress);

  /**
   * 
   * @param emailAddress
   * @param request
   */
  void addFunds(String emailAddress, AddFundsRequest request);

  /**
   * 
   * @param emailAddress
   * @param request
   * @return
   */
  UserProfileDto updateProfile(String emailAddress, UpdateProfileRequest request);

  /**
   * 
   * @param emailAddress
   * @return
   */
  List<String> getSearchHistory(String emailAddress);

  /**
   * 
   * @param emailAddress
   * @param query
   */
  void addSearchHistory(String emailAddress, String query);

  /**
   * 
   * @param emailAddress
   * @param index
   */
  void removeSearchHistory(String emailAddress, int index);

  /**
   * 
   * @param emailAddress
   * @param playlistName
   * @return
   * @throws EntityAlreadyExistsException
   */
  boolean createPlaylist(String emailAddress, String playlistName)
      throws EntityAlreadyExistsException;

  /**
   * 
   * @param emailAddress
   * @param playlistName
   * @param song
   * @return
   * @throws EntityDoesNotExistException
   */
  boolean addSongToPlaylist(String emailAddress, String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException;

  /**
   * 
   * @param emailAddress
   * @param playlistName
   * @param song
   * @return
   * @throws EntityDoesNotExistException
   */
  boolean removeSongFromPlaylist(String emailAddress, String playlistName, SongFileEntity song)
      throws EntityDoesNotExistException;

  /**
   * 
   * @param emailAddress
   * @param song
   * @return
   * @throws EntityDoesNotExistException
   */
  boolean addSongToMyFavoritesPlaylist(String emailAddress, SongFileEntity song)
      throws EntityDoesNotExistException;

  /**
   * 
   * @param emailAddress
   * @param song
   * @return
   * @throws EntityDoesNotExistException
   */
  boolean removeSongFromMyFavoritesPlaylist(String emailAddress, SongFileEntity song)
      throws EntityDoesNotExistException;

  /**
   * 
   * @param event
   */
  void handleSongAddedToQueueEvent(SongAddedToQueueEvent event);
}
