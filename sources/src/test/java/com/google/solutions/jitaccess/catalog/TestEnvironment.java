//
// Copyright 2024 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.AccessControlList;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestEnvironment {
  private static final UserId SAMPLE_USER_1 = new UserId("user-1@example.com");

  // -------------------------------------------------------------------------
  // export.
  // -------------------------------------------------------------------------

  @Test
  public void export_whenAccessDenied() {
    var policy = new EnvironmentPolicy(
      "env",
      "env",
      new Policy.Metadata("test", Instant.EPOCH));
    var environment = new Environment(
      policy,
      Subjects.create(SAMPLE_USER_1));

    assertFalse(environment.canExport());
    assertFalse(environment.export().isPresent());
  }

  @Test
  public void export() {
    var policy = new EnvironmentPolicy(
      "env",
      "env",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER_1, PolicyPermission.EXPORT.toMask())
        .build(),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));
    var environment = new Environment(
      policy,
      Subjects.create(SAMPLE_USER_1));

    assertTrue(environment.canExport());
    assertTrue(environment.export().isPresent());
  }
}
