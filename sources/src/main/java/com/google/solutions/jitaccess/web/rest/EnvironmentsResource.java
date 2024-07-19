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
import com.google.solutions.jitaccess.catalog.EnvironmentView;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.catalog.policy.PolicyHeader;
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
import java.util.stream.Collectors;

@Dependent
@Path("/api/catalog")
@RequireIapPrincipal
public class EnvironmentsResource {
  @Inject
  Catalog catalog;

  /**
   * Get list of environments, limited to basic information.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments")
  public @NotNull EnvironmentsResource.EnvironmentsInfo list() {
    var environments = this.catalog.environments()
      .stream()
      .map(env -> EnvironmentInfo.fromPolicyHeader(env))
      //TODO: sort
      .collect(Collectors.toList());

    return new EnvironmentsInfo(
      new Link("environments"),
      environments);
  }

  /**
   * Get environment details, including the list of systems that
   * the current user is allowed to view.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("environments/{environment}")
  public @NotNull EnvironmentInfo get(
    @PathParam("environment") @NotNull String environment
  ) throws AccessDeniedException {
    return this.catalog
      .environment(environment)
      .map(env -> EnvironmentInfo.fromEnvironmentView(env))
      .orElseThrow(() -> new AccessDeniedException(
        "The environment does not exist or access is denied"));
  }


  /**
   * Export the environment policy.
   */
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("environments/{environment}/policy")
  public @NotNull String export(
    @PathParam("environment") @NotNull String environment
  ) throws AccessDeniedException {
    return this.catalog
      .environment(environment)
      .flatMap(EnvironmentView::export)
      .map(PolicyDocument::toString)
      .orElseThrow(() -> new AccessDeniedException(
        "The environment does not exist or access is denied"));
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record EnvironmentsInfo(
    @NotNull Link self,
    @NotNull List<EnvironmentInfo> environments
  ) implements CatalogInfo {
  }

  public record EnvironmentInfo(
    @NotNull Link self,
    @Nullable Link export,
    @NotNull String name,
    @NotNull String description,
    @Nullable List<SystemsResource.SystemInfo> systems
  ) implements CatalogInfo {

    static EnvironmentInfo fromPolicyHeader(
      @NotNull PolicyHeader policy
    ) {
      return new EnvironmentInfo(
        new Link("environments/%s", policy.name()),
        null,
        policy.name(),
        policy.description(),
        null);
    }

    static EnvironmentInfo fromEnvironmentView(@NotNull EnvironmentView environment) {
      return new EnvironmentInfo(
        new Link("environments/%s", environment.policy().name()),
        environment.canExport()
          ? new Link("/api/catalog/environments/%s/policy", environment.policy().name())
          : null,
        environment.policy().name(),
        environment.policy().description(),
        environment.systems()
          .stream()
          .map(sys -> SystemsResource.SystemInfo.fromPolicy(sys.policy()))
          .toList());
    }
  }
}
