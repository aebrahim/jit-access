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

package com.google.solutions.jitaccess.core.catalog.group;

import com.google.solutions.jitaccess.core.GroupEmail;
import com.google.solutions.jitaccess.core.UserEmail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

public class TestPrincipalSet {
  @Test
  public void contains() {
    var set = new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new GroupEmail("ftes@example.com")));

    assertFalse(set.contains(new UserEmail("bob@example.com")));
    assertTrue(set.contains(new UserEmail("alice@example.com")));
    assertTrue(set.contains(new GroupEmail("ftes@example.com")));
  }

  @Test
  public void emptySet() {
    var set = new PrincipalSet(Set.of());
    assertFalse(set.overlaps(new PrincipalSet(Set.of())));
    assertFalse(set.overlaps(new PrincipalSet(Set.of(new UserEmail("bob@example.com")))));
  }

  @Test
  public void superset() {
    var set = new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new UserEmail("bob@example.com"),
      new GroupEmail("ftes@example.com")));

    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new UserEmail("bob@example.com"),
      new GroupEmail("ftes@example.com")))));
  }

  @Test
  public void subset() {
    var set = new PrincipalSet(Set.of(new UserEmail("alice@example.com")));

    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com")))));
    assertFalse(set.overlaps(new PrincipalSet(Set.of(
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new UserEmail("bob@example.com"),
      new GroupEmail("ftes@example.com")))));
  }
}
