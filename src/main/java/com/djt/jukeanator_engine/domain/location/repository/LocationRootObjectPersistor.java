package com.djt.jukeanator_engine.domain.location.repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;

public final class LocationRootObjectPersistor {

  public LocationRootObjectPersistor() {}

  public LocationRootEntity loadLocationsFromDisk(String filePath)
      throws ClassNotFoundException, IOException {

    try (FileInputStream fis = new FileInputStream(filePath);
        BufferedInputStream bis = new BufferedInputStream(fis);
        ObjectInputStream ois = new ObjectInputStream(bis)) {

      return (LocationRootEntity) ois.readObject();
    }
  }

  public void writeLocationsToDisk(LocationRootEntity root, String filePath) throws IOException {

    try (FileOutputStream fos = new FileOutputStream(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {

      oos.writeObject(root);
    }
  }
}
