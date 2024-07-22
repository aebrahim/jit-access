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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.catalog.auth.SubjectResolver;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestStructuredLogger {
  // -------------------------------------------------------------------------
  // info.
  // -------------------------------------------------------------------------
  @Test
  public void info_whenMessageHasNoArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.info("E1", "message");

    assertEquals(
      "{\"severity\":\"INFO\",\"message\":\"message\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  @Test
  public void info_whenMessageHasArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.info("E1", "s=%s, d=%d", "test", 1);

    assertEquals(
      "{\"severity\":\"INFO\",\"message\":\"s=test, d=1\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  // -------------------------------------------------------------------------
  // warn.
  // -------------------------------------------------------------------------

  @Test
  public void warn_whenMessageHasNoArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.warn("E1", "message");

    assertEquals(
      "{\"severity\":\"WARN\",\"message\":\"message\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  @Test
  public void warn_whenMessageHasArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.warn("E1", "s=%s, d=%d", "test", 1);

    assertEquals(
      "{\"severity\":\"WARN\",\"message\":\"s=test, d=1\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  @Test
  public void warn_whenExceptionPassed() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.warn("E1", "exception",
      new IllegalStateException("outer-exception",
        new IllegalArgumentException("inner-exception")));

    assertEquals(
      "{\"severity\":\"WARN\",\"message\":\"exception: outer-exception, caused by IllegalArgumentException: inner-exception\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  // -------------------------------------------------------------------------
  // error.
  // -------------------------------------------------------------------------

  @Test
  public void error_whenMessageHasNoArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.error("E1", "message");

    assertEquals(
      "{\"severity\":\"ERROR\",\"message\":\"message\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  @Test
  public void error_whenMessageHasArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.error("E1", "s=%s, d=%d", "test", 1);

    assertEquals(
      "{\"severity\":\"ERROR\",\"message\":\"s=test, d=1\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }

  @Test
  public void error_whenExceptionPassed() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.error("E1", "exception",
      new IllegalStateException("outer-exception",
        new IllegalArgumentException("inner-exception")));

    assertEquals(
      "{\"severity\":\"ERROR\",\"message\":\"exception: outer-exception, caused by IllegalArgumentException: inner-exception\",\"logging.googleapis.com/labels\":{\"event\":\"E1\"}}\n",
      buffer.toString());
  }


  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  @Nested
  public static class RequestContextLogger {

    @Test
    public void info_whenTraceIdAndUserIdSet() {
      var buffer = new StringBuilder();
      var requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
      requestContext.initialize("GET", "/", "trace-1");
      requestContext.authenticate(
        new UserId("id"),
        new IapDevice("device-id", List.of()));
      var logger = new StructuredLogger.RequestContextLogger(buffer, requestContext);

      logger.info("event-1", "message-1");

      assertEquals(
        "{\"severity\":\"INFO\",\"message\":\"message-1\",\"logging.googleapis.com/labels\":" +
          "{\"device_id\":\"device-id\",\"user_id\":\"id\"," +
          "\"request_path\":\"/\",\"request_method\":\"GET\"," +
          "\"event\":\"event-1\"," +
          "\"device_access_levels\":\"\"},\"logging.googleapis.com/trace\":\"trace-1\"}\n",
        buffer.toString());
    }

    @Test
    public void info_whenTraceIdAndAccessLevelsSet() {
      var buffer = new StringBuilder();
      var requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
      requestContext.initialize("GET", "/", "trace-1");
      requestContext.authenticate(
        new UserId("id"),
        new IapDevice("device-id", List.of("level-1", "level-2")));
      var logger = new StructuredLogger.RequestContextLogger(buffer, requestContext);

      logger.info("event-1", "message-1");

      assertEquals(
        "{\"severity\":\"INFO\",\"message\":\"message-1\",\"logging.googleapis.com/labels\":" +
          "{\"device_id\":\"device-id\",\"user_id\":\"id\"," +
          "\"request_path\":\"/\",\"request_method\":\"GET\",\"event\":\"event-1\"," +
          "\"device_access_levels\":\"level-1, level-2\"}," +
          "\"logging.googleapis.com/trace\":\"trace-1\"}\n",
        buffer.toString());
    }

    @Test
    public void error_whenNotAuthenticated() {
      var buffer = new StringBuilder();
      var requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
      var logger = new StructuredLogger.RequestContextLogger(buffer, requestContext);
      logger.error("event-1", "message-1");

      assertEquals(
        "{\"severity\":\"ERROR\",\"message\":\"message-1\",\"logging.googleapis.com/labels\"" +
          ":{\"event\":\"event-1\"}}\n",
        buffer.toString());
    }
  }
}
