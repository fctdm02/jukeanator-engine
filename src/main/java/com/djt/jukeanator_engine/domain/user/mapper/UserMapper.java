package com.djt.jukeanator_engine.domain.user.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.user.dto.CreditTransactionEntryDto;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistDto;
import com.djt.jukeanator_engine.domain.user.dto.UserDto;
import com.djt.jukeanator_engine.domain.user.dto.UserRootDto;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;

/**
 * @author tmyers
 */
public final class UserMapper {

  private UserMapper() {}

  public static UserRootDto toDto(UserRootEntity root) {

    List<UserDto> dtos = new ArrayList<>();

    for (UserEntity user : root.getUsers()) {
      dtos.add(toDto(user));
    }

    return new UserRootDto(dtos);
  }

  public static UserDto toDto(UserEntity entity) {

    return new UserDto(
        entity.getPersistentIdentity(),
        entity.getFirstName(),
        entity.getLastName(),
        entity.getEmailAddress(),
        entity.getPasswordHash(),
        entity.getNumCredits(),
        entity.getSongPlayHistory(),
        entity.getSearchHistory(),
        toPlaylistDtos(entity.getPlaylists()),
        toTransactionDtos(entity.getTransactions()),
        entity.getRole().name());
  }

  public static List<PlaylistDto> toPlaylistDtos(List<PlaylistEntity> entities) {

    List<PlaylistDto> dtos = new ArrayList<>();

    for (PlaylistEntity entity : entities) {
      dtos.add(new PlaylistDto(entity.getPersistentIdentity(), entity.getOwner(),
          entity.getName(), entity.getSongs()));
    }

    return dtos;
  }

  public static List<CreditTransactionEntryDto> toTransactionDtos(
      Collection<CreditTransactionEntity> entities) {

    List<CreditTransactionEntryDto> dtos = new ArrayList<>();

    for (CreditTransactionEntity entity : entities) {
      dtos.add(new CreditTransactionEntryDto(
          entity.getPersistentIdentity(),
          entity.getLocationId(),
          entity.getAmount(),
          entity.getType(),
          entity.getTimestamp(),
          entity.getSongAlbumId(),
          entity.getSongId(),
          entity.getResultingBalance()));
    }

    return dtos;
  }

  public static UserRootEntity toEntity(UserRootDto dto) {

    UserRootEntity root = new UserRootEntity();

    for (UserDto userDto : dto.getUsers()) {
      root.addUser(toEntity(userDto));
    }

    return root;
  }

  public static UserEntity toEntity(UserDto dto) {

    UserEntity user = new UserEntity(
        dto.getPersistentIdentity(),
        dto.getFirstName(),
        dto.getLastName(),
        dto.getEmailAddress(),
        dto.getPasswordHash(),
        dto.getNumCredits(),
        UserRole.valueOf(dto.getRole()));

    user.setSongPlayHistory(dto.getSongPlayHistory());
    user.setSearchHistory(dto.getSearchHistory());

    // The UserEntity constructor above already seeded a fresh "My Favorites" playlist; discard
    // it in favor of the persisted playlists, which already include it.
    user.getPlaylists().clear();
    for (PlaylistDto playlistDto : dto.getPlaylists()) {
      user.restorePlaylist(new PlaylistEntity(playlistDto.getPersistentIdentity(),
          playlistDto.getOwner(), playlistDto.getName(), playlistDto.getSongs()));
    }

    for (CreditTransactionEntryDto transactionDto : dto.getTransactions()) {
      user.addTransaction(new CreditTransactionEntity(
          transactionDto.getPersistentIdentity(),
          transactionDto.getLocationId(),
          transactionDto.getAmount(),
          transactionDto.getType(),
          transactionDto.getTimestamp(),
          transactionDto.getSongAlbumId(),
          transactionDto.getSongId(),
          transactionDto.getResultingBalance()));
    }

    return user;
  }
}
