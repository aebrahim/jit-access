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
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.CatalogSources;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.Subjects;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.web.EventIds;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestEnvironmentsResource {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");
  private static final Policy.Metadata METADATA = new Policy.Metadata("test", Instant.EPOCH);

  //---------------------------------------------------------------------------
  // list.
  //---------------------------------------------------------------------------

  @Test
  public void environments_returnsSortedList() {
    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = Mockito.mock(Catalog.class);

    when(resource.catalog.environments())
      .thenReturn(List.of(
        new EnvironmentPolicy("env-1", "One", METADATA),
        new EnvironmentPolicy("env-3", "Three", METADATA),
        new EnvironmentPolicy("env-2", "Two", METADATA)));

    var envInfo = resource.list();
    assertEquals(3, envInfo.environments().size());

    assertEquals("env-1", envInfo.environments().get(0).name());
    assertEquals("One", envInfo.environments().get(0).description());

    assertEquals("env-2", envInfo.environments().get(1).name());
    assertEquals("Two", envInfo.environments().get(1).description());

    assertEquals("env-3", envInfo.environments().get(2).name());
    assertEquals("Three", envInfo.environments().get(2).description());
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
      CatalogSources.create(environment));

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(null));
  }

  @Test
  public void get_whenEnvironmentNotFound() throws Exception {
    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(List.of()));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get("unknown"));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_ENVIRONMENTS),
        anyString(),
        any(Exception.class));
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
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environment));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get(environment.name()));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_ENVIRONMENTS),
      anyString(),
      any(Exception.class));
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
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environment));

    var environmentInfo = resource.get(environment.name());
    assertEquals(environment.name(), environmentInfo.name());
    assertEquals(environment.description(), environmentInfo.description());

    assertEquals(1, environmentInfo.systems().size());
    assertSame(allowedSystem.name(), environmentInfo.systems().stream().findFirst().get().name());
  }

  @Test
  public void get_returnsSortedListOfSystems() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
    environment.add(new SystemPolicy("system-2", "Two"));
    environment.add(new SystemPolicy("system-3", "Three"));
    environment.add(new SystemPolicy("system-1", "One"));

    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environment));

    var environmentInfo = resource.get(environment.name());
    var systems = List.copyOf(environmentInfo.systems());
    assertEquals("system-1", systems.get(0).name());
    assertEquals("system-2", systems.get(1).name());
    assertEquals("system-3", systems.get(2).name());
  }

  //---------------------------------------------------------------------------
  // getPolicy.
  //---------------------------------------------------------------------------

  @Test
  public void getPolicy_whenEnvironmentInvalid() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));

    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environment));

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.getPolicy(null));
  }

  @Test
  public void getPolicy_whenEnvironmentNotFound() throws Exception {
    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(List.of()));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.getPolicy("unknown"));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_ENVIRONMENTS),
      anyString(),
      any(Exception.class));
  }

  @Test
  public void getPolicy_whenAccessToEnvironmentDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of(),
      METADATA);

    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      Subjects.create(SAMPLE_USER),
      CatalogSources.create(environment));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.getPolicy(environment.name()));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_ENVIRONMENTS),
      anyString(),
      any(Exception.class));
  }

  @Test
  public void getPolicy() throws AccessException {
    var subject = Subjects.create(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(subject.user(), PolicyPermission.EXPORT.toMask()))),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var resource = new EnvironmentsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = new Catalog(
      subject,
      CatalogSources.create(environment));

    var policy = resource.getPolicy(environment.name());
    assertTrue(policy.policy().contains("schemaVersion: 1"));

    assertEquals("env-1", policy.environment().name());
    assertEquals("Env-1", policy.environment().description());
    assertNull(policy.environment().systems());
    assertEquals(environment.metadata().source(), policy.source());
    assertEquals(environment.metadata().lastModified().getEpochSecond(), policy.lastModified());
  }
}
