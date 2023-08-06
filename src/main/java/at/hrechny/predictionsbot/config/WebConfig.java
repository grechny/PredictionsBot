package at.hrechny.predictionsbot.config;

import at.hrechny.predictionsbot.controller.filter.HashVerificationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class WebConfig {

  private final HashVerificationFilter hashVerificationFilter;

  @Bean
  public FilterRegistrationBean<HashVerificationFilter> hashVerificationFilterRegistrationBean() {
    FilterRegistrationBean<HashVerificationFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(hashVerificationFilter);
    registrationBean.addUrlPatterns("/webapp/*");
    return registrationBean;
  }

}
