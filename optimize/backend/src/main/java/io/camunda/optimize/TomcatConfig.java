/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;

import io.camunda.optimize.rest.HealthRestService;
import io.camunda.optimize.rest.LocalizationRestService;
import io.camunda.optimize.rest.UIConfigurationRestService;
import io.camunda.optimize.rest.constants.RestConstants;
import io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.PanelNotificationConstants;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import io.camunda.optimize.tomcat.ExternalApiRewriteFilter;
import io.camunda.optimize.tomcat.ExternalHomeServlet;
import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import io.camunda.optimize.tomcat.ResponseHeadersFilter;
import io.camunda.optimize.tomcat.URLRedirectFilter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import java.net.URL;
import java.util.Optional;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class TomcatConfig {

  public static final String EXTERNAL_SUB_PATH = "/external";

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TomcatConfig.class);

  private static final String[] COMPRESSED_MIME_TYPES = {
    "application/json", "text/html", "application/x-font-ttf", "image/svg+xml"
  };

  private static final String LOGIN_ENDPOINT = "/login";
  private static final String METRICS_ENDPOINT = "/metrics";
  private static final String URL_BASE = "/#";

  public static final String ALLOWED_URL_EXTENSION =
      String.join(
          "|",
          new String[] {
            URL_BASE,
            LOGIN_ENDPOINT,
            METRICS_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.OAUTH_AUTH_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.OAUTH_REDIRECT_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_JWKS_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_AUTH_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_TOKEN_ENDPOINT,
            CCSaasAuth0WebSecurityConfig.AUTH0_USERINFO_ENDPOINT,
            HealthRestService.READYZ_PATH,
            LocalizationRestService.LOCALIZATION_PATH,
            TomcatConfig.EXTERNAL_SUB_PATH,
            OptimizeResourceConstants.REST_API_PATH,
            OptimizeResourceConstants.STATIC_RESOURCE_PATH,
            OptimizeResourceConstants.ACTUATOR_ENDPOINT,
            PanelNotificationConstants.SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT,
            RestConstants.BACKUP_ENDPOINT,
            UIConfigurationRestService.UI_CONFIGURATION_PATH
          });

  private static final String HTTP11_NIO_PROTOCOL = "org.apache.coyote.http11.Http11Nio2Protocol";

  @Autowired private ConfigurationService configurationService;

  @Autowired private Environment environment;

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatFactoryCustomizer() {
    LOG.debug("Setting up connectors...");
    return new WebServerFactoryCustomizer<TomcatServletWebServerFactory>() {
      @Override
      public void customize(final TomcatServletWebServerFactory factory) {
        final Optional<String> contextPath = getContextPath();
        if (contextPath.isPresent()) {
          factory.setContextPath(contextPath.get());
        }

        // NOTE: With the current implementation, we are always installing 2 connectors,
        //   one for HTTP, one for HTTPs. The latter can be HTTP/1.1 or HTTP/2 depending
        //   on the configuration.

        factory.addConnectorCustomizers(
            connector -> {
              configureHttpConnector(connector);
            });

        factory.addAdditionalTomcatConnectors(
            new Connector() {
              {
                configureHttpsConnector(this);
              }
            });
      }
    };
  }

  @Bean
  ServletContextInitializer externalResourcesServlet() {
    LOG.debug("Registering servlet 'externalResourcesServlet'...");
    return new ServletContextInitializer() {
      @Override
      public void onStartup(final ServletContext servletContext) throws ServletException {
        LOG.debug("Registering bean externalResourcesHandler...");
        final URL webappURL = getClass().getClassLoader().getResource("webapp");
        if (webappURL == null) {
          LOG.debug("Static content directory 'webapp' not found. No bean will be registered.");
          return;
        }

        final String webappPath = webappURL.toExternalForm().replaceFirst("file:", "");
        final ServletRegistration.Dynamic webappServlet =
            servletContext.addServlet("external-home", ExternalHomeServlet.class);
        webappServlet.setInitParameter("resourceBase", webappPath);
        webappServlet.addMapping("/external/*");
        webappServlet.addMapping("/*");
        webappServlet.setLoadOnStartup(1);
      }
    };
  }

  @Bean
  FilterRegistrationBean<ExternalApiRewriteFilter> externalApiRewriter() {
    LOG.debug("Registering filter 'externalApiRewriter'...");
    final FilterRegistrationBean<ExternalApiRewriteFilter> filterRegistrationBean =
        new FilterRegistrationBean<>();

    final String clusterId =
        configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId();

    filterRegistrationBean.setFilter(new ExternalApiRewriteFilter(clusterId));
    filterRegistrationBean.addUrlPatterns("/*");
    return filterRegistrationBean;
  }

  @Bean
  /* redirect to /# when the endpoint is not valid. do this rather than showing an error page */
  FilterRegistrationBean<URLRedirectFilter> urlRedirector() {
    LOG.debug("Registering filter 'urlRedirector'...");
    final String regex =
        "^(?!" + getContextPath().orElse("") + "(" + ALLOWED_URL_EXTENSION + ")).+";
    final URLRedirectFilter filter =
        new URLRedirectFilter(regex, getContextPath().orElse("") + "/#");

    final FilterRegistrationBean<URLRedirectFilter> registrationBean =
        new FilterRegistrationBean<>();
    registrationBean.addUrlPatterns("/*");
    registrationBean.setFilter(filter);
    return registrationBean;
  }

  @Bean
  FilterRegistrationBean<ResponseHeadersFilter> responseHeadersInjector() {
    LOG.debug("Registering filter 'responseHeadersInjector'...");
    final ResponseHeadersFilter responseHeadersFilter =
        new ResponseHeadersFilter(configurationService);
    final FilterRegistrationBean<ResponseHeadersFilter> registrationBean =
        new FilterRegistrationBean<>();
    registrationBean.addUrlPatterns("/*");
    registrationBean.setFilter(responseHeadersFilter);
    return registrationBean;
  }

  public int getPort(final String portType) {
    final String portProperty = environment.getProperty(portType);
    if (portProperty != null) {
      try {
        return Integer.parseInt(portProperty);
      } catch (final NumberFormatException exception) {
        throw new OptimizeConfigurationException("Error while determining container port");
      }
    }

    if (portType.equals(EnvironmentPropertiesConstants.HTTPS_PORT_KEY)) {
      return configurationService.getContainerHttpsPort();
    }

    final Optional<Integer> httpPort = configurationService.getContainerHttpPort();
    if (httpPort.isEmpty()) {
      throw new OptimizeConfigurationException("HTTP port not configured");
    }
    return httpPort.get();
  }

  public Optional<String> getContextPath() {
    // If the property is set by env var (the case when starting a new Optimize in ITs), this takes
    // precedence over config
    final Optional<String> contextPath = Optional.ofNullable(environment.getProperty(CONTEXT_PATH));
    if (contextPath.isEmpty()) {
      return configurationService.getContextPath();
    }
    return contextPath;
  }

  private SSLHostConfig getSslHostConfig() {
    final SSLHostConfig sslHostConfig = new SSLHostConfig();
    sslHostConfig.setHostName(configurationService.getContainerHost());

    final SSLHostConfigCertificate cert =
        new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
    cert.setCertificateKeystoreFile(configurationService.getContainerKeystoreLocation());
    cert.setCertificateKeystorePassword(configurationService.getContainerKeystorePassword());
    sslHostConfig.addCertificate(cert);

    return sslHostConfig;
  }

  private void enableGzipSupport(final Connector connector) {
    connector.setProperty("compression", "on");
    connector.setProperty("compressionMinSize", "23");
    connector.setProperty("compressionNoCompressionMethods", ""); // all methods
    connector.setProperty("useSendfile", "false");
    connector.setProperty("compressableMimeType", String.join(",", COMPRESSED_MIME_TYPES));
  }

  private void setMaxHeaderSize(final Connector connector) {
    // NOTE: In Tomcat, the request and response header size are both controlled
    // by a single property called maxHeaderSize.
    final int maxHeaderSize =
        Math.max(
            configurationService.getMaxResponseHeaderSizeInBytes(),
            configurationService.getMaxRequestHeaderSizeInBytes());
    connector.setProperty("maxHeaderSize", String.valueOf(maxHeaderSize));
  }

  private void configureHttpConnector(final Connector connector) {
    connector.setPort(getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY));
    connector.setScheme("http");
    connector.setSecure(false);
    connector.setXpoweredBy(false); // do not send server version header
    enableGzipSupport(connector);
    setMaxHeaderSize(connector);
  }

  public void configureHttpsConnector(final Connector connector) {
    connector.setPort(getPort(EnvironmentPropertiesConstants.HTTPS_PORT_KEY));
    connector.setScheme("https");
    connector.setSecure(true);
    connector.setXpoweredBy(false); // do not send server version header
    enableGzipSupport(connector);
    setMaxHeaderSize(connector);

    connector.setProperty("protocol", HTTP11_NIO_PROTOCOL);
    if (configurationService.getContainerHttp2Enabled()) {
      connector.addUpgradeProtocol(new Http2Protocol());
    }

    connector.addSslHostConfig(getSslHostConfig());
  }
}
