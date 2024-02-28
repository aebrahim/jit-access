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
import com.google.solutions.jitaccess.catalog.Policies;
import com.google.solutions.jitaccess.catalog.Subjects;
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

public class TestSystemsResource {

  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static Catalog createCatalog(EnvironmentPolicy environment, Subject subject) {
    return new Catalog(
      subject,
      EnvironmentRepositories.create(environment));
  }

  private static Catalog createCatalog(EnvironmentPolicy environment) {
    return createCatalog(environment, Subjects.createSubject(SAMPLE_USER));
  }

  //---------------------------------------------------------------------------
  // get.
  //---------------------------------------------------------------------------

  @Test
  public void get_whenEnvironmentInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment());

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(null, "system"));
  }

  @Test
  public void get_whenSystemInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment());

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(group.system().environment().name(), null));
  }

  @Test
  public void get_whenEnvironmentNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment());

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get("unknown", "system"));
  }

  @Test
  public void get_whenAccessToSystemDenied() {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
    var system = new SystemPolicy(
      "sys-1",
      "Sys-1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of());
    environment.add(system);

    var resource = new SystemsResource();
    resource.catalog = createCatalog(environment, Subjects.createSubject(SAMPLE_USER));

    assertThrows(
      AccessDeniedException.class,
      () ->  resource.get(environment.name(), system.name()));
  }

  @Test
  public void get_whenAccessToSomeGroupsDenied_thenResultIsFiltered() throws Exception {
    var environment = new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
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
      "denied-1",
      "Denied 1",
      AccessControlList.EMPTY, // Empty ACL -> deny all
      Map.of(),
      List.of());
    system.add(allowedGroup);
    system.add(deniedGroup);
    environment.add(system);

    var resource = new SystemsResource();
    resource.catalog = createCatalog(environment, Subjects.createSubject(SAMPLE_USER));

    var systemInfo = resource.get(environment.name(), system.name());
    assertEquals(system.name(), systemInfo.name());
    assertEquals(system.description(), systemInfo.description());
    assertEquals(environment.name(), systemInfo.environment().name());
    assertEquals(environment.description(), systemInfo.environment().description());

    assertEquals(1, systemInfo.groups().size());
    assertSame(allowedGroup.name(), systemInfo.groups().stream().findFirst().get().name());
  }

  @Test
  public void get_whenGroupJoined() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()))),
      Map.of());

    var expiry = Instant.now().plusSeconds(60);
    var subject = Mockito.mock(Subject.class);
    when(subject.user()).thenReturn(SAMPLE_USER);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER), new Principal(group.id(), expiry)));

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment(), subject);

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOINED, groupInfo.access().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertTrue(groupInfo.access().membership().active());
    assertEquals(expiry.getEpochSecond(), groupInfo.access().membership().expiry());
  }

  @Test
  public void get_whenJoinDisallowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.VIEW.toMask()))),
      Map.of());

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment());

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_DISALLOWED, groupInfo.access().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertFalse(groupInfo.access().membership().active());
  }

  @Test
  public void get_whenJoinAllowedWithApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()))),
      Map.of());

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment());

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_ALLOWED_WITH_APPROVAL, groupInfo.access().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertFalse(groupInfo.access().membership().active());
  }

  @Test
  public void get_whenJoinAllowedWithoutApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(
        List.of(
          new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()),
          new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask()))),
      Map.of());

    var resource = new SystemsResource();
    resource.catalog = createCatalog(group.system().environment());

    var systemInfo = resource.get(group.id().environment(), group.id().system());
    assertEquals(1, systemInfo.groups().size());

    var groupInfo = systemInfo.groups().get(0);
    assertEquals(GroupsResource.JoinStatusInfo.JOIN_ALLOWED_WITHOUT_APPROVAL, groupInfo.access().status());

    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.id().toString(), groupInfo.id());
    assertFalse(groupInfo.access().membership().active());
  }
}
