package at.hrechny.predictionsbot.config;

import jakarta.inject.Singleton;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Singleton
public class MessageResolver {

  public String getMessage(String code, Object[] args, Locale locale) {
    var resolvedLocale = locale != null ? locale : Locale.forLanguageTag("ru");
    String pattern;
    try {
      pattern = ResourceBundle.getBundle("messages", resolvedLocale).getString(code);
    } catch (MissingResourceException ex) {
      pattern = code;
    }

    if (args == null || args.length == 0) {
      return pattern;
    }
    return new MessageFormat(pattern, resolvedLocale).format(args);
  }

  public LocalizedMessages forLocale(Locale locale) {
    return new LocalizedMessages(this, locale);
  }
}
