/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;

public interface ProcessInstanceMapper {

  void insert(ProcessInstanceDbModel processInstance);

  ProcessInstanceEntity findOne(Long processInstanceKey);

  Long count(ProcessInstanceDbQuery filter);

  List<ProcessInstanceEntity> search(ProcessInstanceDbQuery filter);
}