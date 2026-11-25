package com.djt.jukeanator_engine.domain.location.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.repository.AbstractRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.location.dto.LocationCreditPackagesDto;
import com.djt.jukeanator_engine.domain.location.dto.LocationRootDto;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
import com.djt.jukeanator_engine.domain.location.mapper.LocationMapper;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/**
 * @author tmyers
 */
public final class LocationRepositoryFileSystemImpl extends AbstractRepositoryFileSystemImpl
    implements LocationRepository {

  static final String CREDIT_PACKAGES_FILENAME = "JukeANator_LocationCreditPackages.json";

  private String filePath;

  public LocationRepositoryFileSystemImpl(String basePath) {
    super(basePath);
    requireNonNull(basePath, "basePath cannot be null");
    new File(basePath).mkdirs();
    this.filePath = basePath + File.separator + LocationRootEntity.LOCATION_LIST_FILENAME;
  }

  @Override
  public void setBasePath(String basePath) {
    requireNonNull(basePath, "basePath cannot be null");
    super.setBasePath(basePath);
    new File(basePath).mkdirs();
    this.filePath = basePath + File.separator + LocationRootEntity.LOCATION_LIST_FILENAME;
  }

  @Override
  public LocationRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    LocationRootDto dto = readJson(filePath, LocationRootDto.class);
    if (dto == null) {
      throw new EntityDoesNotExistException(
          "Could not read location list from disk with naturalIdentity: " + naturalIdentity
              + " and filePath: " + filePath);
    }

    LocationRootEntity root = LocationMapper.toEntity(dto);
    Map<Integer, List<CreditPackageDto>> packages = readCreditPackages();
    for (LocationEntity location : root.getLocations()) {
      location.setCreditPackages(packages.get(location.getPersistentIdentity()));
    }
    return root;
  }

  @Override
  public void storeAggregateRoot(LocationRootEntity root) {

    writeJson(filePath, LocationMapper.toDto(root));
  }

  @Override
  public synchronized void storeCreditPackages(Integer locationId,
      List<CreditPackageDto> packages) {

    requireNonNull(locationId, "locationId cannot be null");
    Map<Integer, List<CreditPackageDto>> all = new TreeMap<>(readCreditPackages());
    if (packages == null || packages.isEmpty()) {
      all.remove(locationId);
    } else {
      all.put(locationId, List.copyOf(packages));
    }
    writeJson(creditPackagesFilePath(), new LocationCreditPackagesDto(all));
  }

  private Map<Integer, List<CreditPackageDto>> readCreditPackages() {

    if (!new File(creditPackagesFilePath()).exists()) {
      return Map.of();
    }
    LocationCreditPackagesDto dto =
        readJson(creditPackagesFilePath(), LocationCreditPackagesDto.class);
    return dto == null ? Map.of() : dto.packagesByLocation();
  }

  private String creditPackagesFilePath() {
    return new File(filePath).getParent() + File.separator + CREDIT_PACKAGES_FILENAME;
  }

  @Override
  public LocationRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    throw new LocationServiceException("This method is unsupported for the file system implementation");
  }

  @Override
  public Integer nextPersistentIdentity() {
    return null;
  }
}
