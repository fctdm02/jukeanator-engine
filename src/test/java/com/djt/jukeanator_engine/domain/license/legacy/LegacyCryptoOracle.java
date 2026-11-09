package com.djt.jukeanator_engine.domain.license.legacy;

import java.io.StringReader;
import java.io.StringWriter;

import javax.crypto.Cipher;

/**
 * Near-verbatim copy of the legacy {@code com.djt.jukeanator.utils.CryptoUtils} encrypt/decrypt
 * and hex routines (jukeanator.com), used only as a test oracle for {@link LegacyCrypto}.
 */
final class LegacyCryptoOracle {

  private LegacyCryptoOracle() {}

  static String encryptText(String text) {
    try {
      Cipher cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.ENCRYPT_MODE, LegacyCrypto.aesKey());
      return convertBytesToHexString(cipher.doFinal(text.getBytes()));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String decryptText(String encryptedText) {
    try {
      Cipher cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.DECRYPT_MODE, LegacyCrypto.aesKey());
      return new String(cipher.doFinal(convertHexStringToBytes(encryptedText)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String convertBytesToHexString(byte[] byteArray) {
    StringWriter sw = new StringWriter();
    for (byte b : byteArray) {
      String s = Integer.toHexString(Byte.valueOf(b).intValue());
      if (s.length() > 2) {
        sw.write(s.substring(s.length() - 2));
      } else if (s.length() == 2) {
        sw.write(s);
      } else if (s.length() == 1) {
        sw.write("0");
        sw.write(s);
      }
    }
    return sw.toString();
  }

  private static byte[] convertHexStringToBytes(String hexString) throws Exception {
    char[] car = new char[2];
    byte[] value = new byte[hexString.length() / 2];
    try (StringReader strr = new StringReader(hexString)) {
      int rc = strr.read(car, 0, 2);
      int i = 0;
      while (rc != -1) {
        int tempint = Integer.parseInt(new String(car), 16);
        if (tempint > 128) {
          tempint = tempint - 256;
        }
        value[i++] = Integer.valueOf(tempint).byteValue();
        rc = strr.read(car, 0, 2);
      }
    }
    return value;
  }
}
