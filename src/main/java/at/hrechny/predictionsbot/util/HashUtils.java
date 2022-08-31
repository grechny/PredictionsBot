package at.hrechny.predictionsbot.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HashUtils {

  @Value("${secrets.telegramKey}")
  private String telegramKey;

  @SneakyThrows
  public String getHash(String originalString) {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(telegramKey.getBytes(StandardCharsets.UTF_8));
    final byte[] hashbytes = digest.digest(originalString.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(hashbytes);
  }

  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

}
