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

package com.google.solutions.jitaccess.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class Coalesce {
  private static final String EMPTY = "";

  /**
   * @return first non-null object
   */
  public  static <T> T objects(
    @Nullable T one,
    @Nullable T two
  ) {
    return one != null ? one : two;
  }

  /**
   * @return first non-null, non-blank string.
   */
  public static String nonEmpty(
    @Nullable String one,
    @Nullable String two
  ) {
    if (one != null && !one.isEmpty()) {
      return one;
    }
    else if (two != null && !two.isEmpty()) {
      return two;
    }
    else {
      return EMPTY;
    }
  }

  /**
   * Return an empty collection if the input collection is null.
   */
  public static <T> @NotNull Collection<T> emptyIfNull(@Nullable Collection<T> c) {
    return (c == null) ? Collections.<T>emptyList() : c;
  }
}
