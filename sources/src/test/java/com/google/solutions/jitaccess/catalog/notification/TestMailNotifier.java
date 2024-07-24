//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.catalog.notification;

import com.google.solutions.jitaccess.apis.clients.SmtpClient;
import com.google.solutions.jitaccess.catalog.auth.EmailAddress;
import com.google.solutions.jitaccess.catalog.auth.EmailMapping;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestMailNotifier {
  private static class TestNotification extends Notifier.Notification {
    private final String templateId;

    protected TestNotification(
      UserId recipient,
      String subject,
      Map<String, Object> properties,
      String templateId
    ) {
      super(
        List.of(recipient),
        List.of(),
        subject);
      this.properties.putAll(properties);
      this.templateId = templateId;
    }

    @Override
    public String getType() {
      return this.templateId;
    }
  }

  // -------------------------------------------------------------------------
  // sendNotification.
  // -------------------------------------------------------------------------

  @Test
  public void whenTemplateNotFound_ThenSendNotificationDoesNotSendMail() throws Exception {
    var mailAdapter = Mockito.mock(SmtpClient.class);
    var service = new MailNotifier(
      mailAdapter,
      new EmailMapping(),
      new MailNotifier.Options(MailNotifier.Options.DEFAULT_TIMEZONE));

    service.sendNotification(new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      new HashMap<String, Object>(),
      "unknown-templateid"));

    verify(mailAdapter, times(0)).sendMail(
      eq(List.of(new EmailAddress("user@example.com"))),
      eq(List.of()),
      eq("Test email"),
      anyString(),
      eq(EnumSet.of(SmtpClient.Flags.NONE)));
  }

  @Test
  public void whenTemplateFound_ThenSendNotificationSendsMail() throws Exception {
    var mailAdapter = Mockito.mock(SmtpClient.class);
    var service = new MailNotifier(
      mailAdapter,
      new EmailMapping(),
      new MailNotifier.Options(MailNotifier.Options.DEFAULT_TIMEZONE));

    service.sendNotification(new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      new HashMap<String, Object>(),
      "RequestActivation"));

    verify(mailAdapter, times(1)).sendMail(
      eq(List.of(new EmailAddress("user@example.com"))),
      eq(List.of()),
      eq("Test email"),
      anyString(),
      eq(EnumSet.of(SmtpClient.Flags.NONE)));
  }

  // -------------------------------------------------------------------------
  // loadResource.
  // -------------------------------------------------------------------------

  @Test
  public void whenTemplateNotFound_ThenLoadResourceReturnsNull() throws Exception
  {
    assertNull(MailNotifier.loadResource("doesnotexist"));
  }
}
