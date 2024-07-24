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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.catalog.JitGroupView;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.Property;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestAbstractDeferrer {
  private final UserId SAMPLE_USER_1 = new UserId("user-1@example.com");
  private final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");
  private final UserId SAMPLE_USER_3 = new UserId("user-3@example.com");

  private final JitGroupId SAMPLE_JITGROUP_ID =  new JitGroupId("env", "sys", "group-1");

  private class SampleDeferrer extends AbstractDeferrer {
    public SampleDeferrer(@NotNull TokenSigner tokenSigner) {
      super(tokenSigner);
    }

    @Override
    void onJoinOperationDeferred(JitGroupView.@NotNull JoinOperation operation) {
    }

    @Override
    void onDeferredJoinOperationCompleted() {
    }
  }

  private class PseudoSigner implements TokenSigner {
    @Override
    public @NotNull TokenWithExpiry sign(
      JsonWebToken.@NotNull Payload payload
    ) {
      if (payload.getFactory() == null) {
        payload.setFactory(new GsonFactory());
      }

      return new ServiceAccountSigner.TokenWithExpiry(payload.toString(), Instant.EPOCH, Instant.MAX);
    }

    @Override
    public JsonWebToken.Payload verify(
      @NotNull String token
    ) throws TokenVerifier.VerificationException {
      try {
        return new GsonFactory().createJsonParser(token).parse(JsonWebToken.Payload.class);
      }
      catch (IOException e) {
        throw new TokenVerifier.VerificationException(e.getMessage());
      }
    }
  }

  //---------------------------------------------------------------------------
  // defer.
  //---------------------------------------------------------------------------

  @Test
  public void defer_whenAssigneesEmpty() throws Exception {
    var signer = Mockito.mock(ServiceAccountSigner.class);
    var deferrer = new SampleDeferrer(signer);

    assertThrows(
      IllegalArgumentException.class,
      () -> deferrer.defer(
      Mockito.mock(JitGroupView.JoinOperation.class),
      Set.of()));
  }

  @Test
  public void defer_whenInputEmpty() throws Exception {
    var operation = Mockito.mock(JitGroupView.JoinOperation.class);
    when(operation.user())
      .thenReturn(SAMPLE_USER_1);
    when(operation.group())
      .thenReturn(SAMPLE_JITGROUP_ID);

    var signer = new PseudoSigner();
    var deferrer = new SampleDeferrer(signer);
    var token = deferrer.defer(operation, Set.of(SAMPLE_USER_2, SAMPLE_USER_3));

    assertEquals(
      "{\"aud\":[\"user-2@example.com\",\"user-3@example.com\"],\"grp\":\"env.sys.group-1\"," +
        "\"usr\":\"user-1@example.com\",\"inp\":{}}",
      token.value());
  }

  @Test
  public void defer_whenInputNotEmpty() throws Exception {
    var property1 = Mockito.mock(Property.class);
    when(property1.name()).thenReturn("prop1");
    when(property1.get()).thenReturn("value1");

    var property2 = Mockito.mock(Property.class);
    when(property2.name()).thenReturn("prop2");
    when(property2.get()).thenReturn(null);

    var operation = Mockito.mock(JitGroupView.JoinOperation.class);
    when(operation.user())
      .thenReturn(SAMPLE_USER_1);
    when(operation.group())
      .thenReturn(SAMPLE_JITGROUP_ID);
    when(operation.input())
      .thenReturn(List.of(property1, property2));

    var signer = new PseudoSigner();
    var deferrer = new SampleDeferrer(signer);
    var token = deferrer.defer(operation, Set.of(SAMPLE_USER_2, SAMPLE_USER_3));

    assertEquals(
      "{\"aud\":[\"user-2@example.com\",\"user-3@example.com\"],\"grp\":\"env.sys.group-1\"," +
        "\"usr\":\"user-1@example.com\",\"inp\":{\"prop1\":\"value1\"}}",
      token.value());
  }

  //---------------------------------------------------------------------------
  // pickup.
  //---------------------------------------------------------------------------

  @Test
  public void pickup_whenInputEmpty() throws Exception {
    var token = "{\"aud\":[\"user-2@example.com\",\"user-3@example.com\"],\"grp\":\"env.sys.group-1\"," +
      "\"usr\":\"user-1@example.com\",\"inp\":{}}";

    var signer = new PseudoSigner();
    var deferrer = new SampleDeferrer(signer);
    var deferral = deferrer.pickup(new Deferrer.DeferralToken(token, null));

    assertEquals(SAMPLE_USER_1, deferral.deferrer());
    assertEquals(2, deferral.assignees().size());
    assertTrue(deferral.assignees().contains(SAMPLE_USER_2));
    assertTrue(deferral.assignees().contains(SAMPLE_USER_3));
    assertTrue(deferral.input().isEmpty());
  }

  @Test
  public void pickup_whenInputNotEmpty() throws Exception {
    var token = "{\"aud\":[\"user-2@example.com\",\"user-3@example.com\"],\"grp\":\"env.sys.group-1\"," +
      "\"usr\":\"user-1@example.com\",\"inp\":{\"prop1\":\"value1\"}}";

    var signer = new PseudoSigner();
    var deferrer = new SampleDeferrer(signer);
    var deferral = deferrer.pickup(new Deferrer.DeferralToken(token, null));

    assertEquals(SAMPLE_USER_1, deferral.deferrer());
    assertEquals(2, deferral.assignees().size());
    assertTrue(deferral.assignees().contains(SAMPLE_USER_2));
    assertTrue(deferral.assignees().contains(SAMPLE_USER_3));
    assertFalse(deferral.input().isEmpty());
    assertEquals(1, deferral.input().size());
    assertEquals("value1", deferral.input().get("prop1"));
  }
}
