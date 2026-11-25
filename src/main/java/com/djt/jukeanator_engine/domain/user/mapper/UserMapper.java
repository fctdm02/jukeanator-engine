package com.djt.jukeanator_engine.domain.user.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.user.dto.PlaylistDto;
import com.djt.jukeanator_engine.domain.user.dto.UserAddFundsTransactionEntryDto;
import com.djt.jukeanator_engine.domain.user.dto.UserDto;
import com.djt.jukeanator_engine.domain.user.dto.UserLocationAccountDto;
import com.djt.jukeanator_engine.domain.user.dto.UserRootDto;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageEntryDto;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserAddFundsTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserLocationAccountEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;
import com.djt.jukeanator_engine.domain.user.model.UserSongCreditUsageEntity;
import com.djt.jukeanator_engine.domain.user.model.UserStatus;

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

    List<UserLocationAccountDto> accounts = new ArrayList<>();
    for (UserLocationAccountEntity account : entity.getLocationAccounts()) {
      accounts.add(toDto(account));
    }

    return new UserDto(
        entity.getPersistentIdentity(),
        entity.getFirstName(),
        entity.getLastName(),
        entity.getEmailAddress(),
        entity.getPasswordHash(),
        entity.getSearchHistory(),
        accounts,
        entity.getRole().name(),
        entity.getStatus().name(),
        entity.getVerificationTokenHash(),
        entity.getVerificationTokenExpiresAt());
  }

  public static UserLocationAccountDto toDto(UserLocationAccountEntity account) {

    return new UserLocationAccountDto(
        account.getPersistentIdentity(),
        account.getLocationId(),
        account.getNumCredits(),
        account.getSongPlayHistory(),
        toPlaylistDtos(account.getPlaylists()),
        toUserSongCreditUsageDtos(account.getSongCreditUsages()),
        toUserAddFundsTransactionDtos(account.getAddFundsTransactions()));
  }

  public static List<PlaylistDto> toPlaylistDtos(List<PlaylistEntity> entities) {

    List<PlaylistDto> dtos = new ArrayList<>();

    for (PlaylistEntity entity : entities) {
      dtos.add(new PlaylistDto(entity.getPersistentIdentity(), entity.getName(),
          entity.getSongs()));
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
          entity.getResultingBalance(),
          entity.getSyncId()));
    }

    return dtos;
  }

  public static List<UserAddFundsTransactionEntryDto> toUserAddFundsTransactionDtos(
      Collection<UserAddFundsTransactionEntity> entities) {

    List<UserAddFundsTransactionEntryDto> dtos = new ArrayList<>();

    for (UserAddFundsTransactionEntity entity : entities) {
      dtos.add(new UserAddFundsTransactionEntryDto(
          entity.getPersistentIdentity(),
          entity.getLocationId(),
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
        UserRole.valueOf(dto.role()));

    user.setSearchHistory(new ArrayList<>(dto.searchHistory()));

    // A user file written before verification existed has no status: treat it as ACTIVE.
    user.setStatus(dto.status() == null ? UserStatus.ACTIVE : UserStatus.valueOf(dto.status()));
    user.setVerificationTokenHash(dto.verificationTokenHash());
    user.setVerificationTokenExpiresAt(dto.verificationTokenExpiresAt());

    for (UserLocationAccountDto accountDto : dto.locationAccounts()) {
      user.restoreLocationAccount(toEntity(accountDto));
    }

    return user;
  }

  public static UserLocationAccountEntity toEntity(UserLocationAccountDto dto) {

    // The rehydration constructor seeds no playlists: the persisted ones (My Favorites included)
    // are restored below.
    UserLocationAccountEntity account = new UserLocationAccountEntity(dto.persistentIdentity(),
        dto.locationId(), dto.numCredits());

    account.setSongPlayHistory(
        dto.songPlayHistory() == null ? new ArrayList<>() : new ArrayList<>(dto.songPlayHistory()));

    if (dto.playlists() != null) {
      for (PlaylistDto playlistDto : dto.playlists()) {
        account.restorePlaylist(new PlaylistEntity(playlistDto.persistentIdentity(),
            playlistDto.name(),
            playlistDto.songs() == null ? new ArrayList<>() : new ArrayList<>(playlistDto.songs())));
      }
    }

    if (dto.songCreditUsages() != null) {
      for (UserSongCreditUsageEntryDto usageDto : dto.songCreditUsages()) {
        account.addSongCreditUsage(new UserSongCreditUsageEntity(
            usageDto.persistentIdentity(),
            usageDto.locationId(),
            usageDto.amount(),
            usageDto.type(),
            usageDto.timestamp(),
            usageDto.songAlbumId(),
            usageDto.songId(),
            usageDto.resultingBalance(),
            usageDto.syncId()));
      }
    }

    if (dto.addFundsTransactions() != null) {
      for (UserAddFundsTransactionEntryDto addFundsDto : dto.addFundsTransactions()) {
        account.addAddFundsTransaction(new UserAddFundsTransactionEntity(
            addFundsDto.persistentIdentity(),
            addFundsDto.locationId(),
            addFundsDto.packageId(),
            addFundsDto.creditsAwarded(),
            addFundsDto.bonusCredits(),
            addFundsDto.amountUsd(),
            addFundsDto.paymentSource(),
            addFundsDto.paymentTransactionId(),
            addFundsDto.timestamp(),
            addFundsDto.resultingBalance()));
      }
    }

    return account;
  }
}
