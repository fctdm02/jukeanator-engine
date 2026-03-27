package com.djt.jukeanator_engine.domain.songlibrary.service.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;

/**
 * @author tmyers
 * 
 *         Reads and writes to the filesystem the object input/output stream for the song library
 */
public final class SongLibraryObjectPersistor {

  public SongLibraryObjectPersistor() {  
  }

  public RootFolderEntity loadSongLibrary(String filePath) throws ClassNotFoundException, IOException {

    try (FileInputStream fis = new FileInputStream(filePath);
        BufferedInputStream bis = new BufferedInputStream(fis);
        ObjectInputStream ois = new ObjectInputStream(bis)) {

      return (RootFolderEntity) ois.readObject();
    }
  }

  public void writeSongLibraryToDisk(RootFolderEntity root, String filePath) throws IOException {

    try (FileOutputStream fos = new FileOutputStream(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {

      oos.writeObject(root);
    }
  }
}
