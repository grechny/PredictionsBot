package at.hrechny.predictionsbot.mapper;

import java.time.Year;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class YearMapper {

  public String asString(Year year) {
    return year != null ? year.toString() : null;
  }

  public Year asYear(String string) {
    if (StringUtils.isBlank(string)) {
      return null;
    }

    return Year.parse(string);
  }

}
