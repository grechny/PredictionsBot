package at.hrechny.predictionsbot.util;

import us.dustinj.timezonemap.TimeZoneMap;

public class TimeZoneUtils {

  private final static TimeZoneMap timeZoneMap = TimeZoneMap.forEverywhere();

  public static String getTimeZone(Float latitude, Float longitude) {
    var timeZone = timeZoneMap.getOverlappingTimeZone(latitude, longitude);
    return timeZone != null ? timeZone.getZoneId() : null;
  }

}
