/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.unit.DataSize;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/** Represents an instance of the {@link BrokerModuleConfiguration} Spring application. */
@SuppressWarnings("UnusedReturnValue")
public final class TestStandaloneCamunda extends TestSpringApplication<TestStandaloneCamunda>
    implements TestGateway<TestStandaloneCamunda> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestStandaloneCamunda.class);
  private static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private static final String ELASTICSEARCH_EXPORTER_ID = "elasticsearchExporter";
  private static final String CAMUNDA_EXPORTER_ID = "camundaExporter";
  private final ElasticsearchContainer esContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI
  private final BrokerBasedProperties brokerProperties;
  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;
  private final Map<String, Consumer<ExporterCfg>> registeredExporters = new HashMap<>();

  public TestStandaloneCamunda() {
    super(
        CommonsModuleConfiguration.class,
        OperateModuleConfiguration.class,
        TasklistModuleConfiguration.class,
        WebappsModuleConfiguration.class,
        BrokerModuleConfiguration.class,
        // test overrides - to control data clean up; (and some components are not installed on
        // Tests)
        TestOperateElasticsearchSchemaManager.class,
        TestTasklistElasticsearchSchemaManager.class,
        TestOperateSchemaStartup.class,
        TestTasklistSchemaStartup.class,
        IndexTemplateDescriptorsConfigurator.class);

    brokerProperties = new BrokerBasedProperties();

    brokerProperties.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());

    // set a smaller default log segment size since we pre-allocate, which might be a lot in tests
    // for local development; also lower the watermarks for local testing
    brokerProperties.getData().setLogSegmentSize(DataSize.ofMegabytes(16));
    brokerProperties.getData().getDisk().getFreeSpace().setProcessing(DataSize.ofMegabytes(128));
    brokerProperties.getData().getDisk().getFreeSpace().setReplication(DataSize.ofMegabytes(64));

    brokerProperties.getExperimental().getConsistencyChecks().setEnableForeignKeyChecks(true);
    brokerProperties.getExperimental().getConsistencyChecks().setEnablePreconditions(true);

    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();

    //noinspection resource
    withBean("config", brokerProperties, BrokerBasedProperties.class)
        .withBean("operate-config", operateProperties, OperateProperties.class)
        .withBean("tasklist-config", tasklistProperties, TasklistProperties.class)
        .withAdditionalProfile(Profile.BROKER)
        .withAdditionalProfile(Profile.OPERATE)
        .withAdditionalProfile(Profile.TASKLIST)
        .withAdditionalInitializer(new WebappsConfigurationInitializer());

    // default exporters
    withRecordingExporter(true).withElasticsearchExporter(true);
  }

  @Override
  public TestStandaloneCamunda start() {
    esContainer.start();

    final String esURL = String.format("http://%s", esContainer.getHttpHostAddress());

    operateProperties.getElasticsearch().setUrl(esURL);
    operateProperties.getZeebeElasticsearch().setUrl(esURL);
    tasklistProperties.getElasticsearch().setUrl(esURL);
    tasklistProperties.getZeebeElasticsearch().setUrl(esURL);

    setExportersConfig();
    return super.start();
  }

  @Override
  public TestStandaloneCamunda stop() {
    // clean up ES/OS indices
    LOGGER.info("Stopping standalone camunda test...");
    esContainer.stop();
    return super.stop();
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case COMMAND -> brokerProperties.getNetwork().getCommandApi().getPort();
      case GATEWAY -> brokerProperties.getGateway().getNetwork().getPort();
      case CLUSTER -> brokerProperties.getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    // because @ConditionalOnRestGatewayEnabled relies on the zeebe.broker.gateway.enable property,
    // we need to hook in at the last minute and set the property as it won't resolve from the
    // config bean
    final String esURL = String.format("http://%s", esContainer.getHttpHostAddress());

    withProperty("zeebe.broker.gateway.enable", brokerProperties.getGateway().isEnable());
    withProperty("camunda.rest.query.enabled", true);
    withProperty("camunda.database.url", esURL);
    return super.createSpringBuilder();
  }

  public TestRestOperateClient newOperateClient() {
    return new TestRestOperateClient(restAddress());
  }

  @Override
  public TestStandaloneCamunda self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(brokerProperties.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    return brokerProperties.getNetwork().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return BrokerHealthActuator.of(monitoringUri().toString());
  }

  @Override
  public boolean isGateway() {
    return brokerProperties.getGateway().isEnable();
  }

  @Override
  public URI grpcAddress() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    return TestGateway.super.grpcAddress();
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  @Override
  public TestStandaloneCamunda withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(brokerProperties.getGateway());
    return this;
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return brokerProperties.getGateway();
  }

  @Override
  public ZeebeClientBuilder newClientBuilder() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Cannot create a new client for this broker, as it does not have an embedded gateway");
    }

    return TestGateway.super.newClientBuilder();
  }

  /** Returns the broker configuration */
  public BrokerBasedProperties brokerConfig() {
    return brokerProperties;
  }

  /**
   * Modifies the broker configuration. Will still mutate the configuration if the broker is
   * started, but likely has no effect until it's restarted.
   */
  public TestStandaloneCamunda withBrokerConfig(final Consumer<BrokerBasedProperties> modifier) {
    modifier.accept(brokerProperties);
    return this;
  }

  /**
   * Enables/disables usage of the recording exporter using {@link #RECORDING_EXPORTER_ID} as its
   * unique ID.
   *
   * @param useRecordingExporter if true, will enable the exporter; if false, will remove it from
   *     the config
   * @return itself for chaining
   */
  public TestStandaloneCamunda withRecordingExporter(final boolean useRecordingExporter) {
    if (!useRecordingExporter) {
      registeredExporters.remove(RECORDING_EXPORTER_ID);
      return this;
    }
    return withExporter(
        RECORDING_EXPORTER_ID, cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  /**
   * Enables/disables usage of the elasticsearch exporter using {@link #ELASTICSEARCH_EXPORTER_ID}
   * as its unique ID.
   *
   * @param useElasticsearchExporter if true, will enable the exporter; if false, will remove it
   *     from the config
   * @return itself for chaining
   */
  public TestStandaloneCamunda withElasticsearchExporter(final boolean useElasticsearchExporter) {
    if (!useElasticsearchExporter) {
      registeredExporters.remove(ELASTICSEARCH_EXPORTER_ID);
      return this;
    }
    return withExporter(
        ELASTICSEARCH_EXPORTER_ID,
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(Map.of("url", "http://" + esContainer.getHttpHostAddress()));
        });
  }

  /**
   * Enables usage of the camunda exporter using {@link #CAMUNDA_EXPORTER_ID} as its unique ID.
   *
   * @return itself for chaining
   */
  public TestStandaloneCamunda withCamundaExporter() {
    withExporter(
        CAMUNDA_EXPORTER_ID,
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of("url", "http://" + esContainer.getHttpHostAddress()),
                  "bulk",
                  Map.of("size", 1)));
        });
    return this;
  }

  /**
   * Registers or replaces a new exporter with the given ID. If it was already registered, the
   * existing configuration is passed to the modifier.
   *
   * @param id the ID of the exporter
   * @param modifier a configuration function
   * @return itself for chaining
   */
  public TestStandaloneCamunda withExporter(final String id, final Consumer<ExporterCfg> modifier) {
    registeredExporters.merge(id, modifier, (key, cfg) -> cfg.andThen(modifier));
    return this;
  }

  private void setExportersConfig() {
    registeredExporters.forEach(
        (id, exporterModifier) -> {
          final var cfg = new ExporterCfg();
          exporterModifier.accept(cfg);
          brokerProperties.getExporters().put(id, cfg);
        });
  }

  /**
   * Sets the broker's working directory, aka its data directory. If a path is given, the broker
   * will not delete it on shutdown.
   *
   * @param directory path to the broker's root data directory
   * @return itself for chaining
   */
  public TestStandaloneCamunda withWorkingDirectory(final Path directory) {
    return withBean(
        "workingDirectory", new WorkingDirectory(directory, false), WorkingDirectory.class);
  }
}
