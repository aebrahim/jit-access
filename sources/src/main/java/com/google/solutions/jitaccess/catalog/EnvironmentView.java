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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import com.google.solutions.jitaccess.catalog.policy.SystemPolicy;
import com.google.solutions.jitaccess.util.NullaryOptional;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Environment in the context of a specific subject.
 */
public class EnvironmentView {
  private final @NotNull EnvironmentPolicy policy;
  private final @NotNull Subject subject;
  private final @NotNull Provisioner provisioner;

  EnvironmentView(
    @NotNull EnvironmentPolicy policy,
    @NotNull Subject subject,
    @NotNull Provisioner provisioner
  ) {
    this.policy = policy;
    this.subject = subject;
    this.provisioner = provisioner;
  }

  /**
   * Get environment policy.
   */
  public @NotNull EnvironmentPolicy policy() {
    return this.policy;
  }

  /**
   * Check if the current user is allowed to export the policy.
   *
   * Requires EXPORT access.
   */
  public boolean canExport() {
    return this.policy.isAllowedByAccessControlList(
      this.subject,
      EnumSet.of(PolicyPermission.EXPORT));
  }

  /**
   * Export the environment policy. Requires EXPORT access.
   */
  public Optional<PolicyDocument> export() {
    return NullaryOptional
      .ifTrue(canExport())
      .map(() -> new PolicyDocument(this.policy));
  }

  /**
   * List system policies for which the subject has VIEW access.
   */
  public @NotNull Collection<SystemView> systems() {

    return this.policy
      .systems()
      .stream()
      .filter(sys -> sys.isAllowedByAccessControlList(this.subject, EnumSet.of(PolicyPermission.VIEW)))
      .map(sys -> new SystemView(sys, this.subject, this.provisioner))
      .toList();
  }

  /**
   * Get system policy. Requires VIEW access.
   */
  public @NotNull Optional<SystemView> system(@NotNull String name) {
    Preconditions.checkArgument(name != null, "Name must not be null");

    return this.policy
      .system(name)
      .filter(env -> env.isAllowedByAccessControlList(this.subject, EnumSet.of(PolicyPermission.VIEW)))
      .map(sys -> new SystemView(sys, this.subject, this.provisioner));
  }
}
