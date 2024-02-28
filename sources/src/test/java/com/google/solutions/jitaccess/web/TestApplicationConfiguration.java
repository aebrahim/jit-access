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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestApplicationConfiguration {

  // -------------------------------------------------------------------------
  // environmentNames.
  // -------------------------------------------------------------------------

  @Test
  public void environmentNames_whenNonConfigured() {
    var configuration = new ApplicationConfiguration(Map.of());
    assertEquals(0, configuration.environmentNames().size());
  }

  @Test
  public void environmentNames_whenEmpty() {
    var configuration = new ApplicationConfiguration(
      Map.of(
        ApplicationConfiguration.ENVIRONMENT_PREFIX + "one", " ",
        ApplicationConfiguration.ENVIRONMENT_PREFIX + "two", ""
      ));
    assertEquals(0, configuration.environmentNames().size());
  }

  @Test
  public void environmentNames() {
    var configuration = new ApplicationConfiguration(
      Map.of(
        ApplicationConfiguration.ENVIRONMENT_PREFIX + "one_env", "...",
        ApplicationConfiguration.ENVIRONMENT_PREFIX + "two_env", "..."
      ));
    assertEquals(2, configuration.environmentNames().size());
    assertTrue(configuration.environmentNames().contains("one-env"));
    assertTrue(configuration.environmentNames().contains("two-env"));
  }

  // -------------------------------------------------------------------------
  // environment.
  // -------------------------------------------------------------------------

  @Test
  public void environment_whenNotFound() {
    var configuration = new ApplicationConfiguration(Map.of());
    var setting = configuration.environment("not-found");

    assertNotNull(setting);
    assertFalse(setting.isValid());
    assertThrows(IllegalStateException.class, () -> setting.value());
  }

  // -------------------------------------------------------------------------
  // environmentCacheTimeout.
  // -------------------------------------------------------------------------

  @Test
  public void environmentCacheTimeout() {
    assertEquals(
      Duration.ofMinutes(1),
      new ApplicationConfiguration(
        Map.of("RESOURCE_CACHE_TIMEOUT", "60")).environmentCacheTimeout.value());
    assertEquals(
      Duration.ofMinutes(5),
      new ApplicationConfiguration(Map.of()).environmentCacheTimeout.value());
  }
}
