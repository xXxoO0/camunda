/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION_PREFIX;
import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;
import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_SERVICE_TOKEN;
import static io.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_FLAG;
import static io.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_STRICT_VALUE;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AuthCookieService.class);
  private final ConfigurationService configurationService;

  public AuthCookieService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public List<Cookie> createDeleteOptimizeAuthCookies() {
    LOG.trace("Deleting Optimize authentication cookie(s).");
    // We don't know how many cookies need deleting here is depends on token size, so we just delete
    // a sensible default of 5.
    final List<Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      cookies.add(createDeleteCookie(getAuthorizationCookieNameWithSuffix(i), "", "https"));
    }
    return cookies;
  }

  public List<Cookie> createDeleteOptimizeAuthNewCookie(final boolean secure) {
    LOG.trace("Deleting Optimize authentication cookie.");
    // We don't know how many cookies need deleting here is depends on token size, so we just delete
    // a sensible default of 5.
    final List<Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final Cookie cookie = new Cookie(getAuthorizationCookieNameWithSuffix(i), "");
      cookie.setPath(getCookiePath());
      cookie.setDomain(null);
      cookie.setMaxAge(0);
      cookie.setSecure(secure);
      cookie.setHttpOnly(true);
      cookies.add(cookie);
    }
    return cookies;
  }

  public Cookie createDeleteOptimizeRefreshCookie() {
    LOG.trace("Deleting Optimize refresh cookie.");
    return createDeleteCookie(OPTIMIZE_REFRESH_TOKEN, "", "https");
  }

  public Cookie createDeleteOptimizeRefreshNewCookie(final boolean secure) {
    LOG.trace("Deleting Optimize refresh cookie.");
    final Cookie cookie = new Cookie(OPTIMIZE_REFRESH_TOKEN, "");
    cookie.setPath(getCookiePath());
    cookie.setDomain(null);
    cookie.setMaxAge(0);
    cookie.setSecure(secure);
    cookie.setHttpOnly(true);
    return cookie;
  }

  public Optional<Instant> getOptimizeAuthCookieTokenExpiryDate(
      final String optimizeAuthCookieToken) {
    return getTokenIssuedAt(optimizeAuthCookieToken)
        .map(Date::toInstant)
        .map(
            issuedAt ->
                issuedAt.plus(
                    getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES));
  }

  public Cookie createDeleteCookie(
      final String cookieName, final String cookieValue, final String requestScheme) {
    return createCookie(cookieName, cookieValue, Instant.now(), requestScheme, true);
  }

  public List<Cookie> createOptimizeAuthCookies(
      final String cookieValue, final Instant expiresAt, final String requestScheme) {
    final int maxCookieLength =
        configurationService.getAuthConfiguration().getCookieConfiguration().getMaxSize();
    final int numberOfCookies = (int) Math.ceil((double) cookieValue.length() / maxCookieLength);
    final List<Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < numberOfCookies; i++) {
      cookies.add(
          createCookie(
              getAuthorizationCookieNameWithSuffix(i),
              extractTokenisedCookieValue(i, numberOfCookies, maxCookieLength, cookieValue),
              expiresAt,
              requestScheme,
              false));
    }
    return cookies;
  }

  public Cookie createCookie(
      final String cookieName,
      final String cookieValue,
      final Instant expiresAt,
      final String requestScheme) {
    return createCookie(cookieName, cookieValue, expiresAt, requestScheme, false);
  }

  public List<Cookie> createOptimizeServiceTokenCookies(
      final OAuth2AccessToken accessToken, final Instant expiresAt, final String requestScheme) {
    LOG.trace("Creating Optimize service token cookie(s).");
    final String tokenValue = accessToken.getTokenValue();
    final int maxCookieLength =
        configurationService.getAuthConfiguration().getCookieConfiguration().getMaxSize();
    final int numberOfCookies = (int) Math.ceil((double) tokenValue.length() / maxCookieLength);
    final List<jakarta.servlet.http.Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < numberOfCookies; i++) {
      cookies.add(
          createCookie(
              getServiceCookieNameWithSuffix(i),
              extractTokenisedCookieValue(i, numberOfCookies, maxCookieLength, tokenValue),
              expiresAt,
              requestScheme));
    }
    return cookies;
  }

  public static Optional<String> getAuthCookieToken(final HttpServletRequest servletRequest) {
    final String authCookieValues =
        Collections.list(servletRequest.getAttributeNames()).stream()
            .filter(name -> name.startsWith(OPTIMIZE_AUTHORIZATION_PREFIX))
            .sorted(
                Comparator.comparingInt(s -> Integer.parseInt(s.substring(s.lastIndexOf('_') + 1))))
            .map(servletRequest::getAttribute)
            .map(String.class::cast)
            .collect(Collectors.joining());
    if (!authCookieValues.isEmpty()) {
      return Optional.of(authCookieValues);
    } else {
      final List<Cookie> cookies =
          Optional.ofNullable(servletRequest.getCookies())
              .map(Arrays::asList)
              .orElse(Collections.emptyList());
      return extractJoinedCookieValueFromCookies(cookies)
          .or(() -> extractAuthorizationValueFromCookieHeader(servletRequest))
          .flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
    }
  }

  public static Optional<String> getServiceAccessToken(final HttpServletRequest servletRequest) {
    boolean serviceTokenExtracted = false;
    int serviceTokenSuffixToExtract = 0;
    final StringBuilder serviceAccessToken = new StringBuilder();
    while (!serviceTokenExtracted) {
      final String serviceCookieName = getServiceCookieNameWithSuffix(serviceTokenSuffixToExtract);
      String authorizationValue = null;
      if (servletRequest.getCookies() != null) {
        for (final jakarta.servlet.http.Cookie cookie : servletRequest.getCookies()) {
          if (serviceCookieName.equals(cookie.getName())) {
            authorizationValue = cookie.getValue();
          }
        }
      }
      if (authorizationValue != null) {
        serviceAccessToken.append(authorizationValue);
        serviceTokenSuffixToExtract += 1;
      } else {
        serviceTokenExtracted = true;
      }
    }
    if (serviceAccessToken.length() != 0) {
      return Optional.of(serviceAccessToken.toString().trim());
    }
    return Optional.empty();
  }

  public static Optional<String> getTokenSubject(final String token) {
    return getTokenAttribute(token, DecodedJWT::getSubject);
  }

  public static String createOptimizeAuthCookieValue(final String tokenValue) {
    return AUTH_COOKIE_TOKEN_VALUE_PREFIX + tokenValue;
  }

  public static String getAuthorizationCookieNameWithSuffix(final int suffix) {
    return OPTIMIZE_AUTHORIZATION_PREFIX + suffix;
  }

  private String getCookiePath() {
    return "/"
        + configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId();
  }

  private boolean isSecureScheme(final String requestScheme) {
    return configurationService
        .getAuthConfiguration()
        .getCookieConfiguration()
        .resolveSecureFlagValue(requestScheme);
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }

  private static Optional<Date> getTokenIssuedAt(final String token) {
    return getTokenAttribute(token, DecodedJWT::getIssuedAt);
  }

  private static <T> Optional<T> getTokenAttribute(
      final String token, final Function<DecodedJWT, T> getTokenAttributeFunction) {
    try {
      final DecodedJWT decoded = JWT.decode(token);
      return Optional.of(getTokenAttributeFunction.apply(decoded));
    } catch (final Exception e) {
      LOG.debug("Could not decode security token to extract attribute!", e);
    }
    return Optional.empty();
  }

  private static String getServiceCookieNameWithSuffix(final int suffix) {
    return OPTIMIZE_SERVICE_TOKEN + "_" + suffix;
  }

  private static Optional<String> extractTokenFromAuthorizationValue(final String authCookieValue) {
    if (authCookieValue != null && authCookieValue.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
      return Optional.of(authCookieValue.substring(AUTH_COOKIE_TOKEN_VALUE_PREFIX.length()).trim());
    }
    return Optional.ofNullable(authCookieValue);
  }

  public static Optional<String> extractJoinedCookieValueFromCookies(final List<Cookie> cookies) {
    final String cookieValue =
        cookies.stream()
            .filter(cookie -> cookie.getName().startsWith(OPTIMIZE_AUTHORIZATION_PREFIX))
            .sorted(
                Comparator.comparingInt(
                    s -> Integer.parseInt(s.getName().substring(s.getName().lastIndexOf('_') + 1))))
            .map(Cookie::getValue)
            .collect(Collectors.joining());
    if (cookieValue.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(cookieValue);
  }

  private Cookie createCookie(
      final String cookieName,
      final String cookieValue,
      final Instant expiresAt,
      final String requestScheme,
      final boolean isDelete) {
    final String cookiePath = getCookiePath();
    final jakarta.servlet.http.Cookie cookie =
        new jakarta.servlet.http.Cookie(cookieName, cookieValue);
    cookie.setPath(cookiePath);
    cookie.setHttpOnly(true);
    cookie.setSecure(isSecureScheme(requestScheme));
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      cookie.setAttribute(SAME_SITE_COOKIE_FLAG, SAME_SITE_COOKIE_STRICT_VALUE);
    }

    if (expiresAt == null) {
      cookie.setMaxAge(-1);
    } else {
      cookie.setMaxAge(isDelete ? 0 : (int) Duration.between(Instant.now(), expiresAt).toSeconds());
    }

    return cookie;
  }

  private static Optional<String> extractAuthorizationValueFromCookieHeader(
      final HttpServletRequest servletRequest) {
    final String cookieHeader = servletRequest.getHeader("Cookie");
    if (cookieHeader != null) {
      // In the header we have a series of values of the type a=b;c=d;d=e
      final String[] cookiePairs = cookieHeader.split(";");
      for (final String cookiePair : cookiePairs) {
        // We are looking for "foo" in something like X-Optimize-Authorization_bar=foo
        final Pattern pattern = Pattern.compile(OPTIMIZE_AUTHORIZATION_PREFIX + "\\d+=([^;]+)");
        final Matcher matcher = pattern.matcher(cookiePair);
        if (matcher.find()) {
          // We found it, so now we extract the value
          final String value = matcher.group(1);
          // Trim white spaces and tabs to get only the value
          return Optional.of(value.replace("\"", "").trim());
        }
      }
    }
    return Optional.empty();
  }

  private static String extractTokenisedCookieValue(
      final int i, final int numberOfCookies, final int maxCookieLength, final String cookieValue) {
    /* creates a substring of the cookie token value based on the index 'i' and the maximum cookie length
     'maxCookieLength'. If the current index 'i' is equal to the number of cookies minus one, it takes the
     remaining characters of the token value from the current index multiplied by the maximum cookie length
     until the end of the string, otherwise it takes a substring starting from the current index multiplied by
     the maximum cookie length with a length of the maximum cookie length.
    */
    return i == (numberOfCookies - 1)
        ? cookieValue.substring((i * maxCookieLength))
        : cookieValue.substring((i * maxCookieLength), ((i * maxCookieLength) + maxCookieLength));
  }
}
