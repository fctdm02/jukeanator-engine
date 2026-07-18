package com.djt.jukeanator_engine.domain.user.repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.djt.jukeanator_engine.domain.user.model.CreditLedgerRootEntity;

public final class CreditLedgerRootObjectPersistor {

  public CreditLedgerRootObjectPersistor() {}

  public CreditLedgerRootEntity loadLedgerFromDisk(String filePath)
      throws ClassNotFoundException, IOException {

    try (FileInputStream fis = new FileInputStream(filePath);
        BufferedInputStream bis = new BufferedInputStream(fis);
        ObjectInputStream ois = new ObjectInputStream(bis)) {

      return (CreditLedgerRootEntity) ois.readObject();
    }
  }

  public void writeLedgerToDisk(CreditLedgerRootEntity root, String filePath) throws IOException {

    try (FileOutputStream fos = new FileOutputStream(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {

      oos.writeObject(root);
    }
  }
}
