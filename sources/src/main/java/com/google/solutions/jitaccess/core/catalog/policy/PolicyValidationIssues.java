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

package com.google.solutions.jitaccess.core.catalog.policy;

import org.jetbrains.annotations.NotNull;

import java.util.*;

class PolicyValidationIssues {
  private final @NotNull LinkedList<String> errors = new LinkedList<>();
  private final @NotNull LinkedList<String> warnings = new LinkedList<>();

  private @NotNull String currentContext = "file";

  void setContext(@NotNull String context) {
    this.currentContext = context;
  }

  void addWarning(@NotNull String format, Object... args) {
    var message = new Formatter()
      .format(format, args)
      .toString();

    this.warnings.addLast(
      String.format("[%s] %s", this.currentContext, message));
  }

  void addError(@NotNull String format, Object... args) {
    var message = new Formatter()
      .format(format, args)
      .toString();

    this.errors.addLast(
      String.format("[%s] %s", this.currentContext, message));
  }

  public boolean isSuccessful() {
    return this.errors.isEmpty();
  }

  public List<String> getErrors() {
    return Collections.unmodifiableList(this.errors);
  }

  public List<String> getWarnings() {
    return Collections.unmodifiableList(this.warnings);
  }
}
