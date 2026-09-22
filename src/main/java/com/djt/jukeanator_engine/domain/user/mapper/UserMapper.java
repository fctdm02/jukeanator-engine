package com.djt.jukeanator_engine.domain.user.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistDto;
import com.djt.jukeanator_engine.domain.user.dto.UserAddFundsTransactionEntryDto;
import com.djt.jukeanator_engine.domain.user.dto.UserDto;
import com.djt.jukeanator_engine.domain.user.dto.UserRootDto;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageEntryDto;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;

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
        toUserSongCreditUsageDtos(entity.getUserSongCreditUsages()),
        toUserAddFundsTransactionDtos(entity.getUserAddFundsTransactions()),
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

  public static List<UserSongCreditUsageEntryDto> toUserSongCreditUsageDtos(
      Collection<UserSongCreditUsageEntity> entities) {

    List<UserSongCreditUsageEntryDto> dtos = new ArrayList<>();

    for (UserSongCreditUsageEntity entity : entities) {
      dtos.add(new UserSongCreditUsageEntryDto(
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

  public static List<UserAddFundsTransactionEntryDto> toUserAddFundsTransactionDtos(
      Collection<UserAddFundsTransactionEntity> entities) {

    List<UserAddFundsTransactionEntryDto> dtos = new ArrayList<>();

    for (UserAddFundsTransactionEntity entity : entities) {
      dtos.add(new UserAddFundsTransactionEntryDto(
          entity.getPersistentIdentity(),
          entity.getPackageId(),
          entity.getCreditsAwarded(),
          entity.getBonusCredits(),
          entity.getAmountUsd(),
          entity.getPaymentSource(),
          entity.getPaymentTransactionId(),
          entity.getTimestamp(),
          entity.getResultingBalance()));
    }

    return dtos;
  }

  public static UserRootEntity toEntity(UserRootDto dto) {

    UserRootEntity root = new UserRootEntity();

    for (UserDto userDto : dto.users()) {
      root.addUser(toEntity(userDto));
    }

    return root;
  }

  public static UserEntity toEntity(UserDto dto) {

    UserEntity user = new UserEntity(
        dto.persistentIdentity(),
        dto.firstName(),
        dto.lastName(),
        dto.emailAddress(),
        dto.passwordHash(),
        dto.numCredits(),
        UserRole.valueOf(dto.role()));

    user.setSongPlayHistory(dto.songPlayHistory());
    user.setSearchHistory(dto.searchHistory());

    // The UserEntity constructor above already seeded a fresh "My Favorites" playlist; discard
    // it in favor of the persisted playlists, which already include it.
    user.getPlaylists().clear();
    for (PlaylistDto playlistDto : dto.playlists()) {
      user.restorePlaylist(new PlaylistEntity(playlistDto.persistentIdentity(),
          playlistDto.owner(), playlistDto.name(), playlistDto.songs()));
    }

    for (UserSongCreditUsageEntryDto usageDto : dto.userSongCreditUsages()) {
      user.addUserSongCreditUsage(new UserSongCreditUsageEntity(
          usageDto.persistentIdentity(),
          usageDto.locationId(),
          usageDto.amount(),
          usageDto.type(),
          usageDto.timestamp(),
          usageDto.songAlbumId(),
          usageDto.songId(),
          usageDto.resultingBalance()));
    }

    for (UserAddFundsTransactionEntryDto addFundsDto : dto.userAddFundsTransactions()) {
      user.addUserAddFundsTransaction(new UserAddFundsTransactionEntity(
          addFundsDto.persistentIdentity(),
          addFundsDto.packageId(),
          addFundsDto.creditsAwarded(),
          addFundsDto.bonusCredits(),
          addFundsDto.amountUsd(),
          addFundsDto.paymentSource(),
          addFundsDto.paymentTransactionId(),
          addFundsDto.timestamp(),
          addFundsDto.resultingBalance()));
    }

    return user;
  }
}
