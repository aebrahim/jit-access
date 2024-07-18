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
    var filteredGroups = this.catalog.groups(environment, system);

    return this.catalog
      .system(environment, system)
      .map(sys -> SystemInfo.fromPolicy(
        sys,
        filteredGroups
          .stream()
          .map(GroupsResource.GroupInfo::fromPolicy)
          .toList()))
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
    @NotNull EnvironmentsResource.EnvironmentInfo environment,
    @Nullable List<GroupsResource.GroupInfo> groups
  ) implements CatalogInfo {
    static SystemInfo fromPolicy(
      @NotNull SystemPolicy policy,
      @Nullable List<GroupsResource.GroupInfo> groups
    ) {
      return new SystemInfo(
        new Link("environments/%s/systems/%s", policy.environment().name(), policy.name()),
        policy.name(),
        policy.description(),
        EnvironmentsResource.EnvironmentInfo.fromPolicyHeader(policy.environment()),
        groups);
    }
  }
}
