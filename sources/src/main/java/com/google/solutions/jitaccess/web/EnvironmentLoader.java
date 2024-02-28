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

package com.google.solutions.jitaccess.web;

import com.google.api.client.util.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.Environment;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyHeader;
import com.google.solutions.jitaccess.util.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Loads and caches environments.
 */
public class EnvironmentLoader implements Catalog.Source {

  /**
   * Map environment name -> policy locator.
   */
  private final @NotNull LoadingCache<String, Environment> environmentCache;

  private final @NotNull GroupMapping groupMapping;
  private final @NotNull Set<String> environmentNames;
  private final @NotNull Function<String, EnvironmentPolicy> producePolicy;
  private final @NotNull Function<EnvironmentPolicy, ResourceManagerClient> produceResourceManagerClient;
  private final @NotNull CloudIdentityGroupsClient groupsClient;
  private final @NotNull Logger logger;

  EnvironmentLoader(
    @NotNull Set<String> environmentNames,
    @NotNull Function<String, EnvironmentPolicy> producePolicy,
    @NotNull Function<EnvironmentPolicy, ResourceManagerClient> produceResourceManagerClient,
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull Duration cacheDuration,
    @NotNull Logger logger
  ) {
    this.environmentNames = environmentNames;
    this.producePolicy = producePolicy;
    this.produceResourceManagerClient = produceResourceManagerClient;
    this.groupMapping = groupMapping;
    this.groupsClient = groupsClient;
    this.logger = logger;

    //
    // Prepare policy cache.
    //
    this.environmentCache = CacheBuilder.newBuilder()
      .expireAfterWrite(cacheDuration)
      .build(new CacheLoader<>() {
        @Override
        public @NotNull Environment load(
          @NotNull String environmentName
        ) {
          var policy = producePolicy.apply(environmentName);

          Preconditions.checkState(
            policy.name().equals(environmentName),
            String.format(
              "The name in the policy ('%s') must match the name used in the configuration ('%s')",
              policy.name(),
              environmentName));

          return new Environment(
            policy,
            groupMapping,
            groupsClient,
            produceResourceManagerClient.apply(policy),
            logger);
        }
      });
  }

  // -------------------------------------------------------------------------
  // Catalog.Source.
  // -------------------------------------------------------------------------

  @Override
  public @NotNull Collection<PolicyHeader> environments() {
    //
    // Avoid eagerly loading all policies just to retrieve their
    // name and descriptions.
    //
    return this.environmentNames
      .stream()
      .map(name -> (PolicyHeader)new PolicyHeader() {
        @Override
        public @NotNull String name() {
          return name;
        }

        @Override
        public @NotNull String description() {
          return name;
        }
      })
      .toList();
  }

  @Override
  public @NotNull Optional<Environment> lookup(
    @NotNull Catalog catalog,
    @NotNull String name
  ) {
    if (!environmentNames.contains(name)) {
      return Optional.empty();
    }

    //
    // Read policy from cache (or load it lazily)
    //
    try {
      //
      // Retrieve configuration. Throws an exception if not found.
      //
      return Optional.of(this.environmentCache.get(name));
    }
    catch (Exception e) {
      this.logger.error(
        EventIds.LOAD_ENVIRONMENT,
        String.format("Loading policy for environment '%s' failed", name),
        Exceptions.unwrap(e));
      return Optional.empty();
    }
  }
}
