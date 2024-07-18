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
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.catalog.policy.PolicyPermission;
import com.google.solutions.jitaccess.util.NullaryOptional;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Environment in the context of a specific subject.
 */
public class Environment {
  private final @NotNull EnvironmentPolicy policy;
  private final @NotNull Subject subject;

  Environment(@NotNull EnvironmentPolicy policy, @NotNull Subject subject) {
    this.policy = policy;
    this.subject = subject;
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
}
