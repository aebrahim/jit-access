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

import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

public class TestPolicyResource {
  //---------------------------------------------------------------------------
  // lint.
  //---------------------------------------------------------------------------

  @Test
  public void lint_whenPolicyMalformed() {
    var resource = new PolicyResource();

    var result = resource.lint("invalid:;)");

    assertFalse(result.successful());
    assertFalse(result.issues().isEmpty());
  }

  @Test
  public void lint_whenPolicyValid() {
    var resource = new PolicyResource();

    var policy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.now()));

    var result = resource.lint(new PolicyDocument(policy).toString());

    assertTrue(result.successful());
    assertTrue(result.issues().isEmpty());
  }
}
