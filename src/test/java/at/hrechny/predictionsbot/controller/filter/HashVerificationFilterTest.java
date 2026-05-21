package at.hrechny.predictionsbot.controller.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.util.HashUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class HashVerificationFilterTest {

  private static final String USER_ID = "42";
  private static final String HASH = "valid-hash";

  @Mock
  private HashUtils hashUtils;

  @Mock
  private FilterChain filterChain;

  private HashVerificationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new HashVerificationFilter(hashUtils);
  }

  @Test
  void doFilterContinuesWhenHashMatchesUserIdFromWebappPath() throws Exception {
    var request = request("/webapp/%s/users/%s/predictions".formatted(HASH, USER_ID));
    var response = new MockHttpServletResponse();
    when(hashUtils.getHash(USER_ID)).thenReturn(HASH);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterRejectsRequestWhenHashDoesNotMatchUserIdFromWebappPath() throws Exception {
    var request = request("/webapp/wrong-hash/users/%s/results".formatted(USER_ID));
    var response = new MockHttpServletResponse();
    when(hashUtils.getHash(USER_ID)).thenReturn(HASH);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getErrorMessage()).isEqualTo("User not found");
    verify(filterChain, never()).doFilter(request, response);
  }

  private MockHttpServletRequest request(String uri) {
    var request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    return request;
  }
}
