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
import com.google.solutions.jitaccess.catalog.SystemView;
import com.google.solutions.jitaccess.catalog.policy.SystemPolicy;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@Dependent
@Path("/api/catalog")
@RequireIapPrincipal
public class SystemsResource {
  @Inject
  Catalog catalog;

  /**
   * Get system details, including the list of groups that
   * the current user is allowed to view.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}/systems/{system}")
  public @NotNull SystemInfo get(
    @PathParam("environment") @NotNull String environment,
    @PathParam("system") @NotNull String system
  ) throws AccessDeniedException {
    return this.catalog
      .environment(environment)
      .flatMap(env -> env.system(system))
      .map(sys -> SystemInfo.create(sys))
      .orElseThrow(() -> new AccessDeniedException(
        "The system does not exist or access is denied"));
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record SystemInfo(
    @NotNull Link self,
    @NotNull String name,
    @NotNull String description,
    @Nullable EnvironmentsResource.EnvironmentInfo environment,
    @Nullable List<GroupsResource.GroupInfo> groups
  ) implements ObjectInfo {

    /**
     * Create SystemInfo with summary information only.
     */
    static SystemInfo createSummary(
      @NotNull SystemPolicy policy
    ) {
      return new SystemInfo(
        new Link("environments/%s/systems/%s", policy.environment().name(), policy.name()),
        policy.name(),
        policy.description(),
        null,
        null);
    }

    /**
     * Create SystemInfo with full details.
     */
    static SystemInfo create(@NotNull SystemView system) {
      var policy = system.policy();

      return new SystemInfo(
        new Link("environments/%s/systems/%s", policy.environment().name(), policy.name()),
        policy.name(),
        policy.description(),
        EnvironmentsResource.EnvironmentInfo.createSummary(policy.environment()),
        system.groups()
          .stream()
          .sorted(Comparator.comparing(grp -> grp.policy().name()))
          .map(GroupsResource.GroupInfo::create)
          .toList());
    }
  }
}
