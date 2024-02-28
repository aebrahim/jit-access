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

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.mockito.Mockito;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

public class Subjects {

  public static Subject createSubject(
    UserId user,
    Set<PrincipalId> otherPrincipals
  ) {
    var subject = Mockito.mock(Subject.class);
    when(subject.user()).thenReturn(user);
    when(subject.principals()).thenReturn(
      Stream.concat(otherPrincipals.stream(), Stream.<PrincipalId>of(user))
        .map(p -> new Principal(p))
        .collect(Collectors.toSet()));

    return subject;
  }

  public static Subject createSubject(UserId user) {
    return createSubject(user, Set.of());
  }
}
