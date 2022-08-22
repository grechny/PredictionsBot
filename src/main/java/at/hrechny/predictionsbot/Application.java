package at.hrechny.predictionsbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Application {

  /**
   * Running an Spring Boot Application
   */
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

}
