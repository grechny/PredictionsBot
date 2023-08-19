package at.hrechny.predictionsbot.util;

import java.util.regex.Pattern;
import org.apache.commons.lang3.RandomStringUtils;

public class NameUtils {

  private NameUtils() {
  }

  private static final String ALLOWED_SYMBOLS = "[\\d -/:-~À-ž\\u0370-\\u03FFА-я∂]";
  private static final Pattern NAME_PATTERN = Pattern.compile("^" + ALLOWED_SYMBOLS + "{3,20}$");

  public static boolean isValid(String name) {
    return name != null && NAME_PATTERN.matcher(name).matches();
  }

  public static String formatName(String name) {
    var nameBuilder = new StringBuilder();
    if (name != null) {
      for(var c  : name.toCharArray()) {
        if (String.valueOf(c).matches(ALLOWED_SYMBOLS)) {
          nameBuilder.append(c);
        }
      }
    }

    var formattedName = nameBuilder.toString().trim();
    if (isValid(formattedName)) {
      return formattedName;
    } else {
      return RandomStringUtils.randomAlphanumeric(6);
    }
  }
}
