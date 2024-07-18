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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.EnvironmentRepositories;
import com.google.solutions.jitaccess.catalog.Subjects;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class TestEnvironmentsResource {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");
  private static final Policy.Metadata METADATA = new Policy.Metadata("test", Instant.EPOCH);

  //---------------------------------------------------------------------------
  // list.
  //---------------------------------------------------------------------------

  @Test
  public void environments() {
    var resource = new EnvironmentsResource();
    resource.catalog = Mockito.mock(Catalog.class);

    when(resource.catalog.environments())
      .thenReturn(List.of(
        new EnvironmentPolicy("env-1", "Env 1", METADATA),
        new EnvironmentPolicy("env-2", "Env 2", METADATA)));

    var envInfo = resource.list();
    assertEquals(2, envInfo.environments().size());

    assertEquals("env-1", envInfo.environments().get(0).name());
    assertEquals("Env 1", envInfo.environments().get(0).description());

    assertEquals("env-2", envInfo.environments().get(1).name());
    assertEquals("Env 2", envInfo.environments().get(1).description());
  }

  //---------------------------------------------------------------------------
  // get.
  //---------------------------------------------------------------------------

  @Test
  public void get_whenEnvironmentInvalid() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));

    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(null));
  }

  @Test
  public void get_whenEnvironmentNotFound() throws Exception {
    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(List.of()));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get("unknown"));
  }

  @Test
  public void get_whenAccessToEnvironmentDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of(),
      METADATA);

    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get(environment.name()));
  }

  @Test
  public void get_whenAccessToSomeSystemsDenied_thenResultIsFiltered() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
    var allowedSystem = new SystemPolicy(
      "system-1",
      "System 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.VIEW.toMask()))),
      Map.of());
    var deniedSystem = new SystemPolicy(
      "denied-1",
      "Denied 1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, -1)
        .build(),
      Map.of());
    environment.add(allowedSystem);
    environment.add(deniedSystem);

    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    var environmentInfo = resource.get(environment.name());
    assertEquals(environment.name(), environmentInfo.name());
    assertEquals(environment.description(), environmentInfo.description());

    assertEquals(1, environmentInfo.systems().size());
    assertSame(allowedSystem.name(), environmentInfo.systems().stream().findFirst().get().name());
  }

  //---------------------------------------------------------------------------
  // export.
  //---------------------------------------------------------------------------

  @Test
  public void export_whenEnvironmentInvalid() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));

    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.export(null));
  }

  @Test
  public void export_whenEnvironmentNotFound() throws Exception {
    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(List.of()));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.export("unknown"));
  }

  @Test
  public void export_whenAccessToEnvironmentDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of(),
      METADATA);

    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.export(environment.name()));
  }

  @Test
  public void export() throws AccessDeniedException {
    var subject = Subjects.create(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(subject.user(), PolicyPermission.EXPORT.toMask()))),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var resource = new EnvironmentsResource();
    resource.catalog = new Catalog(
      subject,
      EnvironmentRepositories.create(environment));

    var yaml = resource.export(environment.name());
    assertTrue(yaml.contains("schemaVersion: 1"));
  }
}
