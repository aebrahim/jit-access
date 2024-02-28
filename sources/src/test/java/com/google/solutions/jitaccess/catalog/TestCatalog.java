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

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class TestCatalog {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static EnvironmentPolicy createEnvironmentPolicy(String name) {
    return new EnvironmentPolicy(
      name,
      name.toUpperCase(),
      new Policy.Metadata("test", Instant.EPOCH));
  }

  // -------------------------------------------------------------------------
  // environments.
  // -------------------------------------------------------------------------

  @Test
  public void environments() {
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      EnvironmentRepositories.create(List.of(
        createEnvironmentPolicy("env-1"),
        createEnvironmentPolicy("env-2"))));

    assertEquals(2, catalog.environments().size());
  }

  // -------------------------------------------------------------------------
  // environment.
  // -------------------------------------------------------------------------

  @Test
  public void environment_whenNotFound() {
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      EnvironmentRepositories.create(createEnvironmentPolicy("env-1")));

    assertFalse(catalog.environment("").isPresent());
    assertFalse(catalog.environment("ENV-1").isPresent());
  }

  @Test
  public void environment_whenAccessDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      AccessControlList.EMPTY,
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      Subjects.createSubject(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertFalse(catalog.environment(environment.name()).isPresent());
  }

  @Test
  public void environment() {
    var subject = Subjects.createSubject(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(subject.user(), PolicyPermission.VIEW.toMask()))),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      subject,
      EnvironmentRepositories.create(environment));

    assertTrue(catalog.environment(environment.name()).isPresent());
  }


  // -------------------------------------------------------------------------
  // environmentPolicy.
  // -------------------------------------------------------------------------

  @Test
  public void environmentPolicy_whenNotFound() {
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      EnvironmentRepositories.create(createEnvironmentPolicy("env-1")));

    assertFalse(catalog.environmentPolicy("").isPresent());
    assertFalse(catalog.environmentPolicy("ENV-1").isPresent());
  }

  @Test
  public void environmentPolicy_whenAccessDenied() {
    var subject = Subjects.createSubject(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(subject.user(), PolicyPermission.VIEW.toMask()))),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      Subjects.createSubject(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertFalse(catalog.environmentPolicy(environment.name()).isPresent());
  }

  @Test
  public void environmentPolicy() {
    var subject = Subjects.createSubject(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(subject.user(), PolicyPermission.EXPORT.toMask()))),
      Map.of(),
      new Policy.Metadata("test", Instant.EPOCH));

    var catalog = new Catalog(
      subject,
      EnvironmentRepositories.create(environment));

    assertTrue(catalog.environmentPolicy(environment.name()).isPresent());
  }

  // -------------------------------------------------------------------------
  // systems.
  // -------------------------------------------------------------------------

  @Test
  public void systems_whenEnvironmentNotFound() {
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      EnvironmentRepositories.create(List.of(
        createEnvironmentPolicy("env-1"))));

    assertEquals(0, catalog.systems("unknown").size());
  }

  @Test
  public void systems_whenAccessPartiallyDenied_thenResultIsFiltered() {
    var allowedSystem = new SystemPolicy(
      "allowed-1",
      "",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask())
      )),
      Map.of());
    var deniedSystem = new SystemPolicy(
      "denied-1",
      "",
      new AccessControlList(List.of(
        new AccessControlList.DeniedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask())
      )),
      Map.of());

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));

    environment.add(allowedSystem);
    environment.add(deniedSystem);

    var catalog = new Catalog(
      Subjects.createSubject(SAMPLE_USER),
      EnvironmentRepositories.create(List.of(environment)));

    var systems = catalog.systems(environment.name());

    assertEquals(1, systems.size());
    assertSame(allowedSystem, systems.stream().findFirst().get());
  }

  // -------------------------------------------------------------------------
  // system.
  // -------------------------------------------------------------------------

  @Test
  public void system_whenNotFound() {
    var environment = createEnvironmentPolicy("env-1");
    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      EnvironmentRepositories.create(environment));

    assertFalse(catalog.system(environment.name(), "notfound").isPresent());
  }

  @Test
  public void system_whenAccessDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy(
      "system-1",
      "System 1",
      AccessControlList.EMPTY,
      Map.of());
    environment.add(system);

    var catalog = new Catalog(
      Subjects.createSubject(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    assertFalse(catalog.system(environment.name(), system.name()).isPresent());
  }

  @Test
  public void system() {
    var subject = Subjects.createSubject(SAMPLE_USER);

    var environment = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy(
      "system-1",
      "System 1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(
        SAMPLE_USER,
        PolicyPermission.VIEW.toMask()))),
      Map.of());
    environment.add(system);

    var catalog = new Catalog(
      subject,
      EnvironmentRepositories.create(environment));

    assertTrue(catalog.system(environment.name(), system.name()).isPresent());
  }

  // -------------------------------------------------------------------------
  // groups.
  // -------------------------------------------------------------------------

  @Test
  public void groups_whenAccessToSomeGroupsDenied_thenResultIsFiltered() throws Exception {
    var environment = createEnvironmentPolicy("env-1");
    var system = new SystemPolicy("system-1", "System 1");
    var allowedGroup = new JitGroupPolicy(
      "allowed-1",
      "Group 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.VIEW.toMask()))),
      Map.of(),
      List.of());
    var deniedGroup = new JitGroupPolicy(
      "group-1",
      "Group 1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of(),
      List.of());
    system.add(allowedGroup);
    system.add(deniedGroup);
    environment.add(system);

    var catalog = new Catalog(
      Subjects.createSubject(SAMPLE_USER),
      EnvironmentRepositories.create(environment));

    var groups = catalog.groups(environment.name(), system.name());
    assertEquals(1, groups.size());
    assertSame(allowedGroup, groups.stream().findFirst().get().group());
  }

  // -------------------------------------------------------------------------
  // group.
  // -------------------------------------------------------------------------

  @Test
  public void group_whenAccessDenied_thenReturnsEmpty() throws AccessDeniedException {
    var environment = createEnvironmentPolicy("env-1");
    var system = new SystemPolicy("system-1", "System 1");
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of(),
      List.of());
    system.add(group);
    environment.add(system);

    var catalog = new Catalog(
      Mockito.mock(Subject.class),
      EnvironmentRepositories.create(environment));

    assertFalse(catalog.group(group.id()).isPresent());
  }

  @Test
  public void group_whenAccessAllowed_thenReturnsDetails() throws Exception {
    var subject = Mockito.mock(Subject.class);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var environment =createEnvironmentPolicy("env-1");
    var system = new SystemPolicy("system-1", "System 1");
    var group = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.VIEW.toMask()))),
      Map.of(),
      List.of());
    system.add(group);
    environment.add(system);

    var catalog = new Catalog(
      subject,
      EnvironmentRepositories.create(environment));

    var details = catalog.group(group.id());
    assertTrue(details.isPresent());
    assertEquals(group, details.get().group());
  }
}
