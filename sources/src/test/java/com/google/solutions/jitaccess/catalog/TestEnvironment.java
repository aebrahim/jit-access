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
import com.google.solutions.jitaccess.catalog.policy.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestEnvironment {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");


  // -------------------------------------------------------------------------
  // systems.
  // -------------------------------------------------------------------------

  @Test
  public void systems_whenAccessPartiallyDenied_thenResultIsFiltered() {
    var allowedSystemPolicy = new SystemPolicy(
      "allowed-1",
      "",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask())
      )),
      Map.of());
    var deniedSystemPolicy = new SystemPolicy(
      "denied-1",
      "",
      new AccessControlList(List.of(
        new AccessControlList.DeniedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask())
      )),
      Map.of());

    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));

    environmentPolicy.add(allowedSystemPolicy);
    environmentPolicy.add(deniedSystemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(List.of(environmentPolicy)));

    var environment = catalog.environment(environmentPolicy.name()).get();
    var systems = environment.systems();

    assertEquals(1, systems.size());
    assertSame(allowedSystemPolicy, systems.stream().findFirst().get());
  }

  // -------------------------------------------------------------------------
  // system.
  // -------------------------------------------------------------------------

  @Test
  public void system_whenNotFound() {
    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environmentPolicy));

    var environment = catalog.environment(environmentPolicy.name()).get();
    assertFalse(environment.system("notfound").isPresent());
  }

  @Test
  public void system_whenAccessDenied() {
    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy(
      "system-1",
      "System 1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, -1)
        .build(),
      Map.of());
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environmentPolicy));

    var environment = catalog.environment(environmentPolicy.name()).get();
    assertFalse(environment.system(systemPolicy.name()).isPresent());
  }

  @Test
  public void system() {
    var subject = Subjects.create(SAMPLE_USER);

    var environmentPolicy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var systemPolicy = new SystemPolicy(
      "system-1",
      "System 1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(
        SAMPLE_USER,
        PolicyPermission.VIEW.toMask()))),
      Map.of());
    environmentPolicy.add(systemPolicy);

    var catalog = new Catalog(
      subject,
      CatalogSources.create(environmentPolicy));

    var environment = catalog.environment(environmentPolicy.name()).get();
    assertTrue(environment.system(systemPolicy.name()).isPresent());
  }

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
      Subjects.create(SAMPLE_USER));

    assertFalse(environment.canExport());
    assertFalse(environment.export().isPresent());
  }

  @Test
  public void export() {
    var policy = new EnvironmentPolicy(
      "env",
      "env",
      new AccessControlList.Builder()
        .allow(SAMPLE_USER, PolicyPermission.EXPORT.toMask())
        .build(),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));
    var environment = new Environment(
      policy,
      Subjects.create(SAMPLE_USER));

    assertTrue(environment.canExport());
    assertTrue(environment.export().isPresent());
  }
}
