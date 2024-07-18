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

package com.google.solutions.jitaccess.catalog.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestJitGroupId {
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsPolicyAndName() {
    Assertions.assertEquals(
      "env.system.name",
      new JitGroupId("env", "system", "name").toString());
  }

  // -------------------------------------------------------------------------
  // Parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"", ".", "..", "a. .c", ".b.", "a.b." ,".b.c"})
  public void parse_whenNullOrEmpty_returnsEmpty(String s) {
    assertFalse(JitGroupId.parse(s).isPresent());
  }

  @Test
  public void parse_toString() {
    var g = new JitGroupId("env", "system", "name");
    assertEquals(g, JitGroupId.parse(g.toString()).get());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    JitGroupId id1 = new JitGroupId("env", "system", "name");
    JitGroupId id2 = new JitGroupId("env", "system", "name");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreEquivalentButDifferInCasing() {
    JitGroupId id1 = new JitGroupId("env", "system", "name");
    JitGroupId id2 = new JitGroupId("env", "system", "Name");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
    assertEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectAreSame() {
    JitGroupId id1 = new JitGroupId("env", "system", "name");

    assertTrue(id1.equals(id1));
    assertEquals(0, id1.compareTo(id1));
  }

  @Test
  public void equals_whenEnvironmentsDiffer() {
    JitGroupId id1 = new JitGroupId("env-1", "system", "name");
    JitGroupId id2 = new JitGroupId("env-2", "system", "name");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenSystemsDiffer() {
    JitGroupId id1 = new JitGroupId("env", "system-1", "name");
    JitGroupId id2 = new JitGroupId("env", "system-2", "name");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenNamesDiffer() {
    JitGroupId id1 = new JitGroupId("env", "system", "name-1");
    JitGroupId id2 = new JitGroupId("env", "system", "name-2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(0, id1.compareTo(id2));
  }

  @Test
  public void equals_whenObjectIsNull() {
    JitGroupId id1 = new JitGroupId("env", "system", "name");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    JitGroupId id1 = new JitGroupId("env", "system", "name");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "env.system.name",
      new JitGroupId("env", "system", "name").value());
  }

  @Test
  public void iamPrincipalId() {
    assertFalse(new JitGroupId("env", "system", "name") instanceof IamPrincipalId);
  }
}
