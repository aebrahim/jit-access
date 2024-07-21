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

import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.catalog.*;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import com.google.solutions.jitaccess.web.EventIds;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class TestGroupsResource {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static Catalog createCatalog(JitGroupPolicy group, Subject subject) {
    return new Catalog(
      subject,
      CatalogSources.create(group.system().environment()));
  }

  private static Catalog createCatalog(JitGroupPolicy group) {
    return createCatalog(group, Subjects.create(SAMPLE_USER));
  }

  //---------------------------------------------------------------------------
  // get.
  //---------------------------------------------------------------------------

  @Test
  public void get_whenGroupIdInvalid() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(group.id().environment(), null, group.name()));
    assertThrows(
      IllegalArgumentException.class,
      () -> resource.get(group.id().environment(), group.id().system(), null));
  }

  @Test
  public void get_whenGroupNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.get(
        group.id().environment(),
        group.id().system(),
        "notfound"));

    verify(resource.logger, times(1)).warn(
      eq(EventIds.API_GROUPS),
      anyString(),
      any(Exception.class));
  }

  @Test
  public void get() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, -1))),
      Map.of(),
      List.of(
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1")),
        new IamRoleBinding(new ProjectId("project-1"), new IamRole("roles/role-1"), "description", null)));

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    var groupInfo = resource.get(group.id().environment(), group.id().system(), group.id().name());
    assertEquals(group.name(), groupInfo.name());
    assertEquals(group.description(), groupInfo.description());
    assertEquals(2, groupInfo.privileges().size());
    assertEquals("roles/role-1 on project-1", groupInfo.privileges().get(0).description());
    assertEquals("description", groupInfo.privileges().get(1).description());
    assertEquals(group.system().name(), groupInfo.system().name());
    assertEquals(group.system().description(), groupInfo.system().description());
    assertEquals(group.system().environment().name(), groupInfo.environment().name());
    assertEquals(group.system().environment().description(), groupInfo.environment().description());
    assertNull(groupInfo.system().environment());
    assertNull(groupInfo.system().groups());
  }

  //---------------------------------------------------------------------------
  // post.
  //---------------------------------------------------------------------------

  @Test
  public void post_whenGroupIdInvalid() {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        null,
        new MultivaluedHashMap<>()));
    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        null,
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenGroupNotFound() throws Exception {
    var group = Policies.createJitGroupPolicy("g-1", AccessControlList.EMPTY, Map.of());

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        "notfound", new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenGroupNotAllowed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList.Builder()
        .deny(SAMPLE_USER, PolicyPermission.JOIN.toMask())
        .build(),
      Map.of());

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenRequiredInputMissing() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, -1))),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "input.var1==true"))
      ));

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenConstraintUnsatisfied() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, -1))),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "input.var1==true"))
      ));

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    var input = new MultivaluedHashMap<String, String>();
    input.putSingle("var1", "False");

    assertThrows(
      PolicyAnalysis.ConstraintUnsatisfiedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        input));
  }

  @Test
  public void post_whenConstraintFailed() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(List.of(new AccessControlList.AllowedEntry(SAMPLE_USER, -1))),
      Map.of(
        Policy.ConstraintClass.JOIN,
        List.of(new CelConstraint(
          "c-1",
          "",
          List.of(new CelConstraint.BooleanVariable("var1", "")),
          "invalid CEL expression"))
      ));

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    var input = new MultivaluedHashMap<String, String>();
    input.putSingle("var1", "False");

    var exception = assertThrows(
      AccessDeniedException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        input));
    assertInstanceOf(PolicyAnalysis.ConstraintFailedException.class, exception.getCause());
  }

  @Test
  public void post_whenExpiryConstraintMissing() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()),
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
      )),
      Map.of());

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    assertThrows(
      UnsupportedOperationException.class,
      () -> resource.post(
        group.id().environment(),
        group.id().system(),
        group.id().name(),
        new MultivaluedHashMap<>()));
  }

  @Test
  public void post_whenJoinAllowedWithoutApproval() throws Exception {
    var group = Policies.createJitGroupPolicy(
      "g-1",
      new AccessControlList(List.of(
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()),
        new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask())
      )),
      Map.of(Policy.ConstraintClass.JOIN, List.of(new ExpiryConstraint(Duration.ofMinutes(1)))));

    var resource = new GroupsResource();
    resource.logger = Mockito.mock(Logger.class);
    resource.catalog = createCatalog(group);

    var groupInfo = resource.post(
      group.id().environment(),
      group.id().system(),
      group.id().name(),
      new MultivaluedHashMap<>());

    assertEquals(GroupsResource.JoinStatusInfo.JOIN_APPROVED, groupInfo.access().status());
    assertTrue(groupInfo.access().membership().active());
    assertEquals(0, groupInfo.access().satisfiedConstraints().size());
    assertEquals(0, groupInfo.access().unsatisfiedConstraints().size());
  }
}
