package at.hrechny.predictionsbot.controller.filter;

import at.hrechny.predictionsbot.util.HashUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HashVerificationFilter implements Filter {

  private final HashUtils hashUtils;

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    var uri = request.getRequestURI();
    var uriParts = uri.split("/");

    var hash = uriParts[2];
    var userId = uriParts[4];

    if (!hashUtils.getHash(userId).equals(hash)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
      return;
    }

    chain.doFilter(servletRequest, servletResponse);
  }
}
