//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.catalog.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestSystemId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsPrefixedValue() {
    assertEquals("system:allAuthenticatedUsers", SystemId.ALL_AUTHENTICATED.toString());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    SystemId id1 = SystemId.ALL_AUTHENTICATED;
    SystemId id2 = SystemId.ALL_AUTHENTICATED;

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    assertFalse(SystemId.ALL_AUTHENTICATED.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    assertFalse(SystemId.ALL_AUTHENTICATED.equals(""));
    assertFalse(SystemId.ALL_AUTHENTICATED.equals(new UserId("user@example.com")));
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals("allAuthenticatedUsers", SystemId.ALL_AUTHENTICATED.value());
  }

  @Test
  public void iamPrincipalId() {
    assertFalse(SystemId.ALL_AUTHENTICATED instanceof IamPrincipalId);
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "system",
    "system:",
    "system"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(SystemId.parse(null).isPresent());
    assertFalse(SystemId.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " system:allAuthenticatedUsers",
    "system:ALLAUTHENTICATEDUSERS  "
  })
  public void parse(String s) {
    assertEquals(SystemId.ALL_AUTHENTICATED, SystemId.parse(s).get());
  }
}
