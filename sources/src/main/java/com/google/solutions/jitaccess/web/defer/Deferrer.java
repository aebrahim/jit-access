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

package com.google.solutions.jitaccess.web.defer;

import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.Deferral;
import com.google.solutions.jitaccess.catalog.JitGroupView;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * Handles deferred join requests, for example by sending an email.
 */
public interface Deferrer {
  /**
   * Express the intent to join a group and solicit approval
   * from an authorized user.
   */
  @NotNull DeferralToken defer(
    @NotNull JitGroupView.JoinOperation op,
    @NotNull Set<UserId> deferees
  ) throws AccessException, IOException;

  /**
   * Pick up a deferral in order to approve a join request
   */
  @NotNull Deferral<JitGroupView.JoinOperation> pickup(
    @NotNull DeferralToken token
  ) throws TokenVerifier.VerificationException;

  /**
   * Token encoding of a deferral, suitable to be passed
   * in URLs and/or email messages.
   */
  record DeferralToken(
    @NotNull String value,
    @Nullable Instant expiryTime
  ) {
    @Override
    public String toString() {
      return this.value;
    }
  }
}
