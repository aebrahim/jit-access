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

import static org.junit.jupiter.api.Assertions.*;

public class TestApplicationConfiguration {

  // -------------------------------------------------------------------------
  // environments.
  // -------------------------------------------------------------------------

  @Test
  public void environments_whenNonConfigured() {
    var configuration = new ApplicationConfiguration(Map.of());
    assertEquals(0, configuration.environments().size());
  }

  @Test
  public void environments_whenEmpty() {
    var configuration = new ApplicationConfiguration(
      Map.of("RESOURCE_ENVIRONMENTS", " "));
    assertEquals(0, configuration.environments().size());
  }

  @Test
  public void environments() {
    var configuration = new ApplicationConfiguration(
      Map.of("RESOURCE_ENVIRONMENTS", ", one-env  , two-env "));
    assertEquals(2, configuration.environments().size());
    assertTrue(configuration.environments().contains("one-env"));
    assertTrue(configuration.environments().contains("two-env"));
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
