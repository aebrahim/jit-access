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
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Catalog of groups that a subject can access.
 *
 * This class serves as the "entry point" for the API/UI to
 * lookup or join groups.
 */
public class Catalog {
  private final @NotNull Catalog.Source source;
  private final @NotNull Subject subject;

  @NotNull Subject subject() {
    return this.subject;
  }

  public Catalog(
    @NotNull Subject subject,
    @NotNull Catalog.Source source
  ) {
    this.subject = subject;
    this.source = source;
  }

  /**
   * Get list of environments. Does not require any permissions
   * because checking permissions would require loading the full
   * policy. To compensate, the method only returns a bare
   * minimum of data.
   */
  public @NotNull Collection<PolicyHeader> environments() {
    return this.source.environmentPolicies();
  }

  /**
   * Get environment policy. Requires VIEW access.
   */
  public @NotNull Optional<Environment> environment(@NotNull String name) {
    Preconditions.checkNotNull(name, "Name must not be null");

    return this.source
      .environmentPolicy(name)
      .filter(env -> env.isAllowedByAccessControlList(this.subject, EnumSet.of(PolicyPermission.VIEW)))
      .map(policy -> new Environment(policy, this.subject));
  }

  /**
   * List system policies for which the subject has VIEW access.
   */
  public @NotNull Collection<SystemPolicy> systems(@NotNull String environmentName) { // TODO: Move to environments?
    Preconditions.checkNotNull(environmentName, "Environment must not be null");

    return this.source
      .environmentPolicy(environmentName)
      .stream()
      .flatMap(env -> env.systems().stream())
      .filter(sys -> sys.isAllowedByAccessControlList(this.subject, EnumSet.of(PolicyPermission.VIEW)))
      .toList();
  }

  /**
   * Get system policy. Requires VIEW access.
   */
  public @NotNull Optional<SystemPolicy> system(
    @NotNull String environmentName,
    @NotNull String name
  ) {
    Preconditions.checkArgument(environmentName != null, "Environment must not be null");
    Preconditions.checkArgument(name != null, "Name must not be null");

    return this.source
      .environmentPolicy(environmentName)
      .flatMap(env -> env.system(name))
      .filter(env -> env.isAllowedByAccessControlList(this.subject, EnumSet.of(PolicyPermission.VIEW)));
  }

  /**
   * List JIT groups for which the subject has VIEW access.
   */
  public @NotNull Collection<JitGroup> groups(
    @NotNull String environmentName,
    @NotNull String systemName
  ) {
    Preconditions.checkArgument(environmentName != null, "Environment name must not be null");

    var provisioner = this.source.provisioner(this, environmentName);

    return this.source
      .environmentPolicy(environmentName)
      .flatMap(env -> env.system(systemName))
      .stream()
      .flatMap(sys -> sys.groups().stream())
      .filter(grp -> grp
        .analyze(this.subject, EnumSet.of(PolicyPermission.VIEW))
        .execute()
        .isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT))
      .map(grp -> new JitGroup(provisioner.get(), grp, this.subject))
      .sorted(Comparator.comparing(g -> g.policy().id()))
      .toList();
  }

  /**
   * Get details for a JIT group. Requires VIEW access.
   *
   * @return group details
   */
  public @NotNull Optional<JitGroup> group(
    @NotNull JitGroupId groupId
  ) {
    Preconditions.checkArgument(groupId != null, "Group ID must not be null");

    var environment = this.source.provisioner(this, groupId.environment());

    return this.source
      .environmentPolicy(groupId.environment())
      .flatMap(env -> env.system(groupId.system()))
      .flatMap(sys -> sys.group(groupId.name()))
      .filter(grp -> grp
        .analyze(this.subject, EnumSet.of(PolicyPermission.VIEW))
        .execute()
      .isAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT))
      .map(grp -> new JitGroup(environment.get(), grp, this.subject));
  }

  /**
   * Source for environment configuration.
   */
  public interface Source {
    /**
     * Get list of summaries for available policies.
     */
    @NotNull Collection<PolicyHeader> environmentPolicies();

    /**
     * Get policy for an environment.
     */
    @NotNull Optional<EnvironmentPolicy> environmentPolicy(@NotNull String name);

    /**
     * Get provisioner for an environment
     */
    @NotNull Optional<Provisioner> provisioner(
      @NotNull Catalog catalog,
      @NotNull String name);
  }
}
