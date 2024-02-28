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

package com.google.solutions.jitaccess.catalog.policy;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Policy for an environment. Environments comprise a number
 * of systems that are managed together. That could be because:
 *
 * - They share the same lifecycle (for ex, "prod" vs "dev")
 * - They are closely related to another (for ex, "backend-systems")
 * - They belong to the same part of the organization (for ex, "marketing")
 */
public class EnvironmentPolicy extends AbstractPolicy {
  static final String NAME_PATTERN = "^[a-zA-Z0-9\\-]{1,16}$";

  private final @NotNull Map<String, SystemPolicy> systems = new TreeMap<>();

  private final @NotNull Metadata metadata;

  public EnvironmentPolicy(
    @NotNull String name,
    @NotNull String description,
    @Nullable AccessControlList acl,
    @NotNull Map<Policy.ConstraintClass, Collection<Constraint>> constraints,
    @NotNull Metadata metadata
  ) {
    super(name, description, acl, constraints);

    Preconditions.checkNotNull(name, "Name must not be null");
    Preconditions.checkArgument(
      name.matches(NAME_PATTERN),
      "Environment names must consist of letters, numbers, and hyphens, and must not exceed 16 characters");

    this.metadata = metadata;
  }

  public EnvironmentPolicy(
    @NotNull String name,
    @NotNull String description,
    @NotNull Metadata metadata
  ) {
    this(name, description, null, Map.of(), metadata);
  }

  public @NotNull EnvironmentPolicy add(@NotNull SystemPolicy system) {
    Preconditions.checkArgument(
      !this.systems.containsKey(system.name()),
      "A system with the same name has already been added");

    system.setParent(this);
    this.systems.put(system.name(), system);
    return this;
  }

  public @NotNull Collection<SystemPolicy> systems() {
    return Collections.unmodifiableCollection(this.systems.values());
  }

  public @NotNull Optional<SystemPolicy> system(@NotNull String name) {
    return Optional.ofNullable(this.systems.get(name));
  }

  @Override
  public @NotNull Metadata metadata() {
    return this.metadata;
  }
}
