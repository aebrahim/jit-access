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

import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class TestJitGroup {
  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  private static EnvironmentPolicy createEnvironmentPolicy() {
    return new EnvironmentPolicy(
      "env-1",
      "Env 1",
      new Policy.Metadata("test", Instant.EPOCH));
  }

  private static Constraint createFailingConstraint(@NotNull String name) throws ConstraintException {
    var check = Mockito.mock(Constraint.Check.class);
    when(check.execute())
      .thenThrow(new IllegalStateException("Mock"));
    when(check.addContext(anyString()))
      .thenReturn(Mockito.mock(Constraint.Context.class));

    var constraint = Mockito.mock(Constraint.class);
    when(constraint.name())
      .thenReturn(name);
    when(constraint.createCheck())
      .thenReturn(check);

    when(check.constraint())
      .thenReturn(constraint);

    return constraint;
  }

  // -------------------------------------------------------------------------
  // join.
  // -------------------------------------------------------------------------

  @Test
  public void join_whenNotAllowed() {
    var subject = Mockito.mock(Subject.class);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var deniedGroup = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.VIEW.toMask()))), // missing JOIN
      Map.of(),
      List.of());

    var group = new JitGroup(
      Mockito.mock(Environment.class),
      deniedGroup,
      subject);

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(deniedGroup));

    var joinOp = group.join();
    assertTrue(joinOp.requiresApproval());
    assertFalse(joinOp
      .dryRun()
      .isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
  }

  @Test
  public void join_whenAllowedWithSelfApprovalButConstraintFails() throws Exception {
    var subject = Mockito.mock(Subject.class);
    when(subject.user())
      .thenReturn(SAMPLE_USER);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var deniedGroup = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList(
        List.of(
          new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.JOIN.toMask()),
          new AccessControlList.AllowedEntry(SAMPLE_USER, PolicyPermission.APPROVE_SELF.toMask()))),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createFailingConstraint("join")),
        Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(deniedGroup));

    var joinOp = new JitGroup(
      Mockito.mock(Environment.class),
      deniedGroup,
      subject).join();
    assertFalse(joinOp.requiresApproval());

    var analysis = joinOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(2, analysis.unsatisfiedConstraints().size()); // JOIN + APPROVE
    assertEquals(2, analysis.failedConstraints().size());      // JOIN + APPROVE
  }

  @Test
  public void join_whenAllowedWithApprovalButConstraintFails() throws Exception {
    var subject = Mockito.mock(Subject.class);
    when(subject.user())
      .thenReturn(SAMPLE_USER);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER)));

    var deniedGroup = new JitGroupPolicy(
      "group-1",
      "Group 1",
      new AccessControlList(
        List.of(new AccessControlList.AllowedEntry(
          SAMPLE_USER,
          PolicyPermission.JOIN.toMask()))),
      Map.of(
        Policy.ConstraintClass.JOIN, List.of(createFailingConstraint("join")),
        Policy.ConstraintClass.APPROVE, List.of(createFailingConstraint("approve"))),
      List.of());

    createEnvironmentPolicy()
      .add(new SystemPolicy("system-1", "System")
        .add(deniedGroup));

    var joinOp = new JitGroup(
      Mockito.mock(Environment.class),
      deniedGroup,
      subject).join();
    assertTrue(joinOp.requiresApproval());

    var analysis = joinOp.dryRun();
    assertTrue(analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS));
    assertEquals(0, analysis.satisfiedConstraints().size());
    assertEquals(1, analysis.unsatisfiedConstraints().size());
    assertEquals(1, analysis.failedConstraints().size());
  }
}
