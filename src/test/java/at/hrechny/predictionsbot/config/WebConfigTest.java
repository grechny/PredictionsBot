package at.hrechny.predictionsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import at.hrechny.predictionsbot.controller.filter.HashVerificationFilter;
import org.junit.jupiter.api.Test;

class WebConfigTest {

  @Test
  void hashVerificationFilterIsRegisteredOnlyForWebappRoutes() {
    var hashVerificationFilter = mock(HashVerificationFilter.class);
    var webConfig = new WebConfig(hashVerificationFilter);

    var registrationBean = webConfig.hashVerificationFilterRegistrationBean();

    assertThat(registrationBean.getFilter()).isSameAs(hashVerificationFilter);
    assertThat(registrationBean.getUrlPatterns()).containsExactly("/webapp/*");
  }
}
