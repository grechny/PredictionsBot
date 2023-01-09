package at.hrechny.predictionsbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Value("${connectors.api-football.minInterval:60}")
  private int apiFootballCacheDuration;

  @Bean
  public CacheManager caffeineCacheManager() {
    var caffeine = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(apiFootballCacheDuration));
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("api-football");
    cacheManager.setCaffeine(caffeine);
    return cacheManager;
  }
}
