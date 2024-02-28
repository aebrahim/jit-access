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
import com.google.solutions.jitaccess.catalog.JitGroup;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.policy.PolicyAnalysis;
import com.google.solutions.jitaccess.catalog.policy.Privilege;
import com.google.solutions.jitaccess.catalog.policy.Property;
import com.google.solutions.jitaccess.util.Coalesce;
import com.google.solutions.jitaccess.web.EventIds;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Dependent
@Path("/api/catalog")
@RequireIapPrincipal
public class GroupsResource {
  @Inject
  Catalog catalog;

  @Inject
  Logger logger;

  /**
   * Get group details, including information about requirements
   * to join the group.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}/groups/{name}")
  public @NotNull GroupInfo get(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name
  ) throws AccessDeniedException {
    var groupId = new JitGroupId(environment, system, name);

    return this.catalog
      .group(groupId)
      .map(GroupInfo::fromPolicy)
      .orElseThrow(() -> new AccessDeniedException("The group does not exist or access is denied"));
  }

  /**
   * Attempt to join the group.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Path("environments/{environment}/systems/{system}/groups/{name}")
  public @NotNull GroupInfo post(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system,
    @PathParam("name") @NotNull String name,
    @NotNull MultivaluedMap<String, String> inputValues
  ) throws AccessException, IOException {
    var groupId = new JitGroupId(environment, system, name);

    var group = this.catalog
      .group(groupId)
      .orElseThrow(() -> new AccessDeniedException("The group does not exist or access is denied"));

    var joinOp = group.join();

    for (var input : joinOp.input()) {
      //
      // Set input. This might throw an exception if the
      // user-provided input it incomplete or invalid.
      //
      if (!inputValues.containsKey(input.name()) && input.isRequired()) {
        throw new IllegalArgumentException(
          String.format("'%s' is a required field", input.displayName()));
      }

      input.set(inputValues.get(input.name()).get(0));
    }

    try {
      if (joinOp.requiresApproval()) {
        throw new UnsupportedOperationException("Approval is not supported yet");
      }
      else {
        var principal = joinOp.execute();

        return GroupInfo.fromPolicy(
          group,
          new JoinInfo(
            JoinStatusInfo.JOIN_APPROVED,
            new MembershipInfo(
              true,
              Optional.ofNullable(principal.expiry())
                .map(Instant::getEpochSecond)
                .get()),
            List.of(), // Don't repeat constraints
            List.of(), // Don't repeat constraints
            joinOp.input()
              .stream()
              .sorted(Comparator.comparing(p -> p.name()))
              .map(InputInfo::fromProperty)
              .toList()));
      }
    }
    catch (PolicyAnalysis.ConstraintFailedException e) {
      //
      // A failed constraint indicates a configuration issue, so
      // log all the details.
      //
      for (var detail : e.exceptions()) {
        this.logger.error(
          EventIds.API_CONSTRAINT_FAILURE,
          detail.getMessage(),
          detail);
      }

      throw new AccessDeniedException(e.getMessage(), e);
    }

    //TODO: log, notify
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record GroupInfo(
    @NotNull Link self,
    @NotNull String id,
    @NotNull String name,
    @NotNull String description,
    @NotNull String cloudIdentityGroup,
    @NotNull List<PrivilegeInfo> privileges,
    @NotNull EnvironmentsResource.EnvironmentInfo environment,
    @NotNull SystemsResource.SystemInfo system,
    @Nullable GroupsResource.JoinInfo access
  ) implements CatalogInfo {
    static GroupInfo fromPolicy(@NotNull JitGroup g) {
      var joinOp = g.join();
      var analysis = joinOp.dryRun();

      JoinStatusInfo status;
      if (analysis.activeMembership().isPresent()) {
        status = JoinStatusInfo.JOINED;
      }
      else if (!analysis.isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS)) {
        status = JoinStatusInfo.JOIN_DISALLOWED;
      }
      else if (joinOp.requiresApproval()) {
        status = JoinStatusInfo.JOIN_ALLOWED_WITH_APPROVAL;
      }
      else {
        status = JoinStatusInfo.JOIN_ALLOWED_WITHOUT_APPROVAL;
      }

      return fromPolicy(
        g,
        JoinInfo.fromAnalysis(
          status,
          analysis));
    }

    static GroupInfo fromPolicy(
      @NotNull JitGroup g,
      @NotNull GroupsResource.JoinInfo joinInfo) {
      return new GroupInfo(
        new Link(
          "environments/%s/systems/%s/groups/%s",
          g.group().id().environment(),
          g.group().id().system(),
          g.group().id().name()),
        g.group().id().toString(),
        g.group().name(),
        g.group().description(),
        g.cloudIdentityGroupId().email,
        g.group()
          .privileges()
          .stream()
          .map(PrivilegeInfo::fromPrivilege)
          .toList(),
        EnvironmentsResource.EnvironmentInfo.fromPolicy(
          g.group().system().environment(),
          null), // Don't list nested systems.
        SystemsResource.SystemInfo.fromPolicy(
          g.group().system(),
          null), // Don't list nested groups.
        joinInfo);
    }
  }

  public record PrivilegeInfo(
    @NotNull String description
  ) {
    static @NotNull PrivilegeInfo fromPrivilege(@NotNull Privilege p) {
      return new PrivilegeInfo(Coalesce.nonEmpty(p.description(), p.toString()));
    }
  }

  public record JoinInfo( // TODO: JoinInfo vs ApproveInfo
    @NotNull JoinStatusInfo status,
    @NotNull MembershipInfo membership,
    @NotNull List<ConstraintInfo> satisfiedConstraints,
    @NotNull List<ConstraintInfo> unsatisfiedConstraints,
    @NotNull List<InputInfo> input
  ) {
    static @NotNull  JoinInfo fromAnalysis(
      @NotNull JoinStatusInfo status,
      @NotNull PolicyAnalysis.Result analysis
    ) {
      return new JoinInfo(
        status,
        new MembershipInfo(
          analysis.activeMembership().isPresent(),
          analysis.activeMembership()
            .map(p -> p.expiry() != null ? p.expiry().getEpochSecond(): null)
            .orElse(null)),
        analysis.satisfiedConstraints().stream()
          .map(c -> new ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.unsatisfiedConstraints().stream()
          .map(c -> new ConstraintInfo(c.name(), c.displayName()))
          .toList(),
        analysis.input().stream()
          .sorted(Comparator.comparing(p -> p.name()))
          .map(InputInfo::fromProperty)
          .toList());
    }
  }

  public enum JoinStatusInfo {
    JOIN_DISALLOWED,
    JOIN_ALLOWED_WITHOUT_APPROVAL,
    JOIN_ALLOWED_WITH_APPROVAL,
    JOIN_REQUESTED,
    JOIN_APPROVED,
    JOINED
  }

  public record MembershipInfo(
    boolean active,
    @Nullable Long expiry
  ) {}


  public record ConstraintInfo(
    @NotNull String name,
    @NotNull String description
  ) {}

  public record InputInfo(
    @NotNull String name,
    @NotNull String description,
    @NotNull String type,
    boolean isRequired,
    @Nullable String value,
    @Nullable String minInclusive,
    @Nullable String maxInclusive
  ) {
    static InputInfo fromProperty(@NotNull Property i) {
      return new InputInfo(
        i.name(),
        i.displayName(),
        i.type().getSimpleName(),
        i.isRequired(),
        i.get(),
        i.minInclusive().orElse(null),
        i.maxInclusive().orElse(null));
    }
  }
}
