/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.builders.OptimizeUpdateRequestOS;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.MetadataIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchMetadataService extends DatabaseMetadataService<OptimizeOpenSearchClient> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OpenSearchMetadataService.class);

  public OpenSearchMetadataService(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public Optional<MetadataDto> readMetadata(final OptimizeOpenSearchClient osClient) {
    final boolean metaDataIndexExists =
        osClient.getRichOpenSearchClient().index().indexExists(METADATA_INDEX_NAME);
    if (!metaDataIndexExists) {
      LOG.info("Optimize Metadata index wasn't found, thus no metadata available.");
      return Optional.empty();
    }
    try {
      final Optional<MetadataDto> metadata =
          osClient
              .getRichOpenSearchClient()
              .doc()
              .getRequest(METADATA_INDEX_NAME, MetadataIndex.ID, MetadataDto.class);
      // We need to do this in two steps instead of returning directly because the log message is
      // necessary
      if (metadata.isEmpty()) {
        LOG.warn(
            "Optimize Metadata index exists but no metadata doc was found, thus no metadata available.");
      }
      return metadata;
    } catch (final OptimizeRuntimeException e) {
      LOG.error(ERROR_MESSAGE_READING_METADATA_DOC, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_READING_METADATA_DOC, e);
    }
  }

  @Override
  protected void upsertMetadataWithScript(
      final OptimizeOpenSearchClient osClient,
      final String schemaVersion,
      final String newInstallationId,
      final ScriptData scriptData) {
    final MetadataDto newMetadataIfAbsent = new MetadataDto(schemaVersion, newInstallationId);
    try {
      final UpdateRequest<MetadataDto, ?> request =
          OptimizeUpdateRequestOS.of(
              b ->
                  b.optimizeIndex(osClient, METADATA_INDEX_NAME)
                      .id(MetadataIndex.ID)
                      .script(
                          sb ->
                              sb.inline(
                                  ib ->
                                      ib.lang(QueryDSL.DEFAULT_SCRIPT_LANG)
                                          .source(scriptData.scriptString())
                                          .params(
                                              scriptData.params().entrySet().stream()
                                                  .collect(
                                                      Collectors.toMap(
                                                          Entry::getKey,
                                                          e -> JsonData.of(e.getValue()))))))
                      .upsert(newMetadataIfAbsent)
                      .refresh(Refresh.True)
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT));

      final UpdateResponse<?> response =
          osClient.getOpenSearchClient().update(request, MetadataDto.class);
      if (!response.result().equals(Result.Created) && !response.result().equals(Result.Updated)) {
        final String errorMsg =
            "Metadata information was neither created nor updated. " + ERROR_MESSAGE_REQUEST;
        LOG.error(errorMsg);
        throw new OptimizeRuntimeException(errorMsg);
      }
    } catch (final IOException e) {
      LOG.error(ERROR_MESSAGE_REQUEST, e);
      throw new OptimizeRuntimeException(ERROR_MESSAGE_REQUEST, e);
    }
  }
}
