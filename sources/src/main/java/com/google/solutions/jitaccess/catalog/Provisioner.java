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

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.Expr;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.common.base.Strings;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.GroupKey;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.auth.IamPrincipalId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.IamRoleBinding;
import com.google.solutions.jitaccess.catalog.policy.JitGroupPolicy;
import com.google.solutions.jitaccess.util.Coalesce;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provisions access to the resources in an environment.
 */
public class Provisioner {
  private final @NotNull GroupProvisioner groupProvisioner;
  private final @NotNull IamProvisioner iamProvisioner;

  Provisioner(
    @NotNull GroupProvisioner groupProvisioner,
    @NotNull IamProvisioner iamProvisioner
  ) {
    this.groupProvisioner = groupProvisioner;
    this.iamProvisioner = iamProvisioner;
  }

  public Provisioner(
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull ResourceManagerClient resourceManagerClient,
    @NotNull Logger logger
  ) {
    this(
      new GroupProvisioner(groupMapping, groupsClient, logger),
      new IamProvisioner(groupsClient, resourceManagerClient, logger));
  }

  /**
   * Provision access to a JIT group.
   */
  public void provisionAccess(
    @NotNull JitGroupPolicy group,
    @NotNull UserId member,
    @NotNull Instant expiry
  ) throws AccessException, IOException {
    //
    // Provision group and membership.
    //
    this.groupProvisioner.provision(group, member, expiry);

    //
    // Provision IAM role bindings in case they have changed.
    //
    this.iamProvisioner.provisionAccess(
      mapGroupId(group),
      group.privileges()
        .stream().filter(p -> p instanceof IamRoleBinding)
        .map(p -> (IamRoleBinding)p)
        .collect(Collectors.toSet()));
  }

  /**
   * Lookup the Cloud Identity group ID for a group.
   */
  public @NotNull GroupId mapGroupId(@NotNull JitGroupPolicy group) {
    return this.groupProvisioner.provisionedGroupId(group);
  }

  /**
   * Provisioner for Cloud Identity groups and memberships.
   */
  public static class GroupProvisioner {
    private final @NotNull GroupMapping mapping;
    private final @NotNull CloudIdentityGroupsClient groupsClient;
    private final @NotNull Logger logger;

    public GroupProvisioner(
      @NotNull GroupMapping mapping,
      @NotNull CloudIdentityGroupsClient groupsClient,
      @NotNull Logger logger
    ) {
      this.mapping = mapping;
      this.groupsClient = groupsClient;
      this.logger = logger;
    }
    /**
     * Lookup the Cloud Identity group ID for a group.
     */
    public @NotNull GroupId provisionedGroupId(@NotNull JitGroupPolicy group) {
      return this.mapping.groupFromJitGroup(group.id());
    }

    /**
     * Provision a temporary group membership. Creates the
     * group if it doesn't exist yet.
     */
    public void provision(
      @NotNull JitGroupPolicy group,
      @NotNull UserId member,
      @NotNull Instant expiry
    ) throws AccessException, IOException {
      //
      // Create group if it doesn't exist yet.
      //
      var groupId = this.mapping.groupFromJitGroup(group.id());

      try {
        var groupKey = this.groupsClient.createGroup(
          groupId,
          CloudIdentityGroupsClient.GroupType.Security,
          String.format(
            "JIT Group %s \u203A %s \u203A %s",
            group.id().environment(),
            group.id().system(),
            group.id().name()),
          group.description());

        //
        // Add user to group.
        //
        this.groupsClient.addMembership(
          groupKey,
          member,
          expiry);

        this.logger.info(
          EventIds.PROVISION_MEMBER,
          "Added %s to group %s with expiry %s",
          member,
          groupId,
          expiry);
      }
      catch (AccessException e) {
        this.logger.error(
          EventIds.PROVISION_MEMBER,
          String.format("Adding %s to group %s failed", member, groupId),
          e);

        throw (AccessException)e.fillInStackTrace();
      }
    }
  }

  /**
   * Provisions IAM policy bindings.
   */
  public static class IamProvisioner {
    private final @NotNull CloudIdentityGroupsClient groupsClient;
    private final @NotNull ResourceManagerClient resourceManagerClient;
    private final @NotNull Logger logger;

    public IamProvisioner(
      @NotNull CloudIdentityGroupsClient groupsClient,
      @NotNull ResourceManagerClient resourceManagerClient,
      @NotNull Logger logger
    ) {
      this.groupsClient = groupsClient;
      this.resourceManagerClient = resourceManagerClient;
      this.logger = logger;
    }

    /**
     * Update an IAM policy in-place:
     *
     * - Remove existing bindings for principal.
     * - Add new bindings.
     */
    static void replaceBindingsForPrincipals(
      @NotNull Policy policy,
      @NotNull IamPrincipalId principal,
      @NotNull Collection<IamRoleBinding> bindings
    ) {
      var prefixedPrincipal = principal.type() + ":" + principal.value();

      //
      // Remove principal from existing bindings.
      //
      var obsoleteBindings = new LinkedList<Binding>();
      for (var existingBinding : policy.getBindings()) {
        existingBinding.getMembers().remove(prefixedPrincipal);

        if (existingBinding.getMembers().isEmpty()) {
          obsoleteBindings.add(existingBinding);
        }
      }

      //
      // Purge bindings for which there is no more principal left.
      //
      policy.getBindings().removeAll(obsoleteBindings);

      //
      // Add new bindings.
      //
      for (var binding : bindings) {
        var condition = Strings.isNullOrEmpty(binding.condition())
          ? null
          : new Expr()
          .setTitle(Coalesce.nonEmpty(binding.description(), "-"))
          .setExpression(binding.condition());

        policy.getBindings().add(new Binding()
          .setMembers(List.of(prefixedPrincipal))
          .setRole(binding.role().name())
          .setCondition(condition));
      }
    }

    /**
     * Provision IAM role bindings for a group, but only do
     * so if the roles have changed or provisioning hasn't
     * happened yet.
     */
    void provisionAccess(
      @NotNull GroupId groupId,
      @NotNull Set<IamRoleBinding> roleBindings
    ) throws AccessException, IOException {

      var groupDetails = this.groupsClient.getGroup(groupId);

      var expectedChecksum = IamBindingChecksum.fromBindings(roleBindings);
      var actualChecksum = IamBindingChecksum.fromTaggedDescription(groupDetails.getDescription());

      if (actualChecksum.equals(expectedChecksum)) {
        //
        // The checksums match, indicating that the role bindings we provisioned
        // previously are still current.
        //
      }
      else {
        logger.info(
          EventIds.PROVISION_IAM_BINDINGS,
          "IAM role bindings for group %s have changed (expected checksum: %s, actual: %s), provisioning...",
          groupId,
          expectedChecksum,
          actualChecksum);

        try {
          //
          // One or more role bindings must have changed, so we need to
          // re-provision all bindings.
          //
          for (var bindingsForResource : roleBindings
            .stream()
            .collect(Collectors.groupingBy(b -> b.resource()))
            .entrySet()) {

            if (bindingsForResource.getKey() instanceof ProjectId projectId) {
              this.resourceManagerClient.modifyIamPolicy(
                projectId,
                policy -> replaceBindingsForPrincipals(policy, groupId, bindingsForResource.getValue()),
                "Provisioning JIT group");
            }
            else {
              throw new UnsupportedOperationException(
                "Unsupported resource type: " + bindingsForResource.getKey().type());
            }
          }

          //
          // Update group.
          //
          this.groupsClient.patchGroup(
            new GroupKey(groupDetails.getName()),
            expectedChecksum.toTaggedDescription(groupDetails.getDescription()));

          logger.info(
            EventIds.PROVISION_IAM_BINDINGS,
            "IAM role bindings for group %s provisioned",
            groupId,
            expectedChecksum,
            actualChecksum);
        }
        catch (AccessException e) {
          this.logger.error(
            EventIds.PROVISION_IAM_BINDINGS,
            String.format("Provisioning IAM role bindings for group %s failed", groupId),
            e);

          throw (AccessException)e.fillInStackTrace();
        }
      }
    }
  }

  /**
   * Checksum over a set of IAM role bindings.
   */
  static class IamBindingChecksum {
    private static final @NotNull Pattern DESCRIPTION_PATTERN = Pattern.compile(".*#([a-f0-9]{2,8})$");

    static final @NotNull IamBindingChecksum ZERO = new IamBindingChecksum(0);

    private final int checksum;

    IamBindingChecksum(int checksum) {
      this.checksum = checksum;
    }

    /**
     * Return a "tagged" description that embeds the checksum.
     */
    public @NotNull String toTaggedDescription(@Nullable String description) {
      var matcher = DESCRIPTION_PATTERN.matcher(Coalesce.nonEmpty(description, ""));
      if (Strings.isNullOrEmpty(description)) {
        return "#" + this;
      }
      else if (matcher.matches()) {
        return new StringBuilder(description).replace(
          matcher.start(1),
          matcher.end(1),
          this.toString()).toString();
      }
      else {
        return String.format("%s #%s", description, this);
      }
    }

    /**
     * Extract checksum from a "tagged" description.
     */
    public static IamBindingChecksum fromTaggedDescription(@Nullable String description) {
      //
      // Extract the hash from the group's description.
      //
      var matcher = DESCRIPTION_PATTERN.matcher(Coalesce.nonEmpty(description, ""));
      if (matcher.matches()) {
        //
        // NB. use parseInt doesn't support hex strings that convert to
        // a negative number, so we use parseLong instead.
        //
        return new IamBindingChecksum((int)Long.parseLong(matcher.group(1), 16));
      }
      else {
        return ZERO;
      }
    }

    /**
     * Calculate checksum over a set of bindings.
     */
    public static IamBindingChecksum fromBindings(@NotNull Set<IamRoleBinding> bindings) {
      int checksum = 0;

      //
      // XOR individual hashes so that the order or bindings is insignificant.
      //
      for (var binding : bindings) {
        checksum ^= binding.checksum();
      }

      return new IamBindingChecksum(checksum);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof IamBindingChecksum other &&
        other != null &&
        other.checksum == this.checksum;
    }

    @Override
    public int hashCode() {
      return this.checksum;
    }

    @Override
    public String toString() {
      return  String.format("%08x", this.checksum);
    }
  }
}
