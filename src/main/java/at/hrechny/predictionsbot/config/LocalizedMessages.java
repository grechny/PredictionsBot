package at.hrechny.predictionsbot.config;

import java.util.Locale;

public class LocalizedMessages {

  private final MessageResolver messageResolver;
  private final Locale locale;

  LocalizedMessages(MessageResolver messageResolver, Locale locale) {
    this.messageResolver = messageResolver;
    this.locale = locale;
  }

  public String message(String code) {
    return messageResolver.getMessage(code, null, locale);
  }
}
