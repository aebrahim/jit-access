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

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.Deferral;
import com.google.solutions.jitaccess.catalog.JitGroupView;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractDeferrer implements Deferrer {
  private final @NotNull TokenSigner tokenSigner;

  protected AbstractDeferrer(@NotNull TokenSigner tokenSigner) {
    this.tokenSigner = tokenSigner;
  }

  /**
   * Notify relevant users about a deferred join operation.
   * @param operation
   */
  abstract void onJoinOperationDeferred(@NotNull JitGroupView.JoinOperation operation);

  /**
   * Notify relevant users about the completion of a deferred join operation.
   */
  abstract void onDeferredJoinOperationCompleted();


  //---------------------------------------------------------------------------
  // Deferrer.
  //---------------------------------------------------------------------------

  @Override
  public @NotNull DeferralToken defer(
    @NotNull JitGroupView.JoinOperation op,
    @NotNull Set<UserId> assignee
  ) throws AccessException, IOException {

    Preconditions.checkArgument(
      !assignee.isEmpty(),
      "At least one assignee must be provided");

    //
    // Encode all inputs into a token and sign it.
    //
    var inputs = new GenericJson();
    op.input().forEach(p -> inputs.set(p.name(), p.get()));

    var payload = new JsonWebToken.Payload()
      .setAudience(assignee
        .stream()
        .sorted()
        .map(UserId::value)
        .toArray())
      .set(Claims.GROUP_ID, op.group().value())
      .set(Claims.USER_ID, op.user().value())
      .set(Claims.INPUT, inputs);

    var signedToken = this.tokenSigner.sign(payload);
    return new DeferralToken(signedToken.token(), signedToken.expiryTime());
  }

  @Override
  public @NotNull Deferral<JitGroupView.JoinOperation> pickup(
    @NotNull DeferralToken token
  ) throws TokenVerifier.VerificationException {
    var payload = this.tokenSigner.verify(token.value());

    var input = ((Map<String, Object>)payload.get(Claims.INPUT))
      .entrySet()
      .stream()
      .collect(Collectors.toMap(e -> e.getKey(), e-> (String)e.getValue()));

    var assignees = (List<String>)payload.getAudience();

    return new DeferredJoin(
      new UserId((String)payload.get(Claims.USER_ID)),
      assignees.stream()
        .map(UserId::new)
        .collect(Collectors.toSet()),
      input);
  }

  private class DeferredJoin implements Deferral<JitGroupView.JoinOperation> {
    private final @NotNull UserId deferrer;
    private @NotNull Set<UserId> assignees;
    private final @NotNull Map<String, String> input;

    DeferredJoin(
      @NotNull UserId deferrer,
      @NotNull Set<UserId> assignees,
      @NotNull Map<String, String> input
    ) {
      this.deferrer = deferrer;
      this.assignees = assignees;
      this.input = input;
    }

    @Override
    public @NotNull UserId deferrer() {
      return this.deferrer;
    }

    @Override
    public @NotNull Set<UserId> assignees() {
      return this.assignees;
    }

    @Override
    public @NotNull Map<String, String> input() {
      return this.input;
    }

    @Override
    public void onCompleted() {
      AbstractDeferrer.this.onDeferredJoinOperationCompleted();
    }
  }

  static class Claims {
    static final String GROUP_ID = "grp";
    static final String USER_ID = "usr";
    static final String INPUT = "inp";
  }
}
