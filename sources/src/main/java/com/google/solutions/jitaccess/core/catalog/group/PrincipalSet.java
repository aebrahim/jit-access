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

import com.google.solutions.jitaccess.core.PrincipalIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

class PrincipalSet {
  private final @NotNull Set<PrincipalIdentifier> principals;

  PrincipalSet(@NotNull Set<PrincipalIdentifier> principals) {
    this.principals = principals;
  }

  boolean contains(PrincipalIdentifier principal) {
    return this.principals.contains(principal);
  }

  /**
   * Check if two sets overlap.
   */
  boolean overlaps(@NotNull Set<PrincipalIdentifier> principals) {
    return this.principals.stream().anyMatch(id -> principals.contains(id));
  }

  /**
   * Check if two sets overlap.
   */
  boolean overlaps(@NotNull PrincipalSet set) {
    return overlaps(set.principals);
  }
}
