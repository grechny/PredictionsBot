package at.hrechny.predictionsbot;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.Micronaut;
import java.time.Clock;

public class Application {

  public static void main(String[] args) {
    Micronaut.run(Application.class, args);
  }

  @Factory
  static class ApplicationFactory {

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

  }
}
