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

package com.google.solutions.jitaccess.catalog.policy;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A constraint that a request, approval, or activation must satisfy.
 */
public interface Constraint {
  /**
   * Unique name.
   */
  @NotNull String name();

  /**
   * Display name.
   */
  @NotNull String displayName();

  /**
   * Create a check object that can be used to evaluate
   * the constraint.
   */
  Check createCheck();

  /**
   * Represents the evaluation of a constraint.
   */
  interface Check {
    /**
     * Constraint that's being checked.
     */
    @NotNull Constraint constraint();

    /**
     * Input properties required to perform a check.
     */
    @NotNull List<Property> input();

    /**
     * Add additional input that might be needed to
     * perform the check.
     */
    @NotNull Context addContext(@NotNull String name);

    /**
     * Perform the actual check, taking all additional
     * input into account.
     */
    boolean execute() throws ConstraintException;
  }

  /**
   * A set of context variables that can be used during
   * constraint evaluation.
   */
  interface  Context {
    /**
     * Add a context variable that can be used during constraint
     * evaluation.
     */
    Context set(@NotNull String name, @NotNull Object val);
  }
}
