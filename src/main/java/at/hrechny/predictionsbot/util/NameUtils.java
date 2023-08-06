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
    if (name != null) {
      name = name.trim().replaceAll(ALLOWED_SYMBOLS, "");
    }

    if (isValid(name)) {
      return name;
    } else {
      return generateName(name);
    }
  }

  private static String generateName(String baseString) {
    if (baseString == null) {
      baseString = "";
    }

    if (baseString.length() > 14) {
      baseString = baseString.substring(0, 14);
    }

    return baseString + '-' + RandomStringUtils.randomAlphanumeric(5);
  }

}
