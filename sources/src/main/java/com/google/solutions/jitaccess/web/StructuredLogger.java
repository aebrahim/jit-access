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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.util.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic logger implementation that writes JSON-structured output.
 */
abstract class StructuredLogger implements Logger {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  protected final @NotNull Appendable output;

  private final @NotNull Map<String, String> extraLabels = new HashMap<>();

  StructuredLogger(@NotNull Appendable output) {
    this.output = output;
  }

  /**
   * Emit the log entry to the log.
   */
  private void log(LogEntry entry) {
    try {
      this.output.append(JSON_MAPPER.writeValueAsString(entry)).append("\n");
    }
    catch (IOException e) {
      try {
        this.output.append(String.format("Failed to log: %s\n", entry.message));
      }
      catch (IOException ignored) {
      }
    }
  }

  protected @NotNull Map<String, String> createLabels(String eventId) {
    var labels = new HashMap<String, String>();
    labels.putAll(this.extraLabels);
    labels.put("event", eventId);
    return labels;
  }

  protected @Nullable String traceId() {
    return null;
  }

  //---------------------------------------------------------------------------
  // Logger.
  //---------------------------------------------------------------------------

  @Override
  public void info(
    @NotNull String eventId,
    @NotNull String message
  ) {
    log(new LogEntry(
      "INFO",
      message,
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void info(
    @NotNull String eventId,
    @NotNull String format,
    Object... args
  ) {
    log(new LogEntry(
      "INFO",
      new Formatter()
        .format(format, args)
        .toString(),
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull String message
  ) {
    log(new LogEntry(
      "WARN",
      message,
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull String format,
    Object... args
  ) {
    log(new LogEntry(
      "WARN",
      new Formatter()
        .format(format, args)
        .toString(),
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void warn(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception
  ) {
    log(new LogEntry(
      "WARN",
      String.format("%s: %s", message, Exceptions.fullMessage(exception)),
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull String message
  ) {
    log(new LogEntry(
      "ERROR",
      message,
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull String format,
    Object... args
  ) {
    log(new LogEntry(
      "ERROR",
      new Formatter()
        .format(format, args)
        .toString(),
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void error(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception
  ) {
    log(new LogEntry(
      "ERROR",
      String.format("%s: %s", message, Exceptions.fullMessage(exception)),
      createLabels(eventId),
      traceId()));
  }

  @Override
  public void addLabel(@NotNull String label, @NotNull String value) {
    this.extraLabels.put(label, value);
  }

  //---------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------

  /**
   * Entry that, when serialized to JSON, can be parsed and interpreted by Cloud Logging.
   */
  public static class LogEntry {
    @JsonProperty("severity")
    private final @NotNull String severity;

    @JsonProperty("message")
    private final @NotNull String message;

    @JsonProperty("logging.googleapis.com/labels")
    private final @NotNull Map<String, String> labels;

    @JsonProperty("logging.googleapis.com/trace")
    private final @Nullable String traceId;

    private LogEntry(
      @NotNull String severity,
      @NotNull String message,
      @NotNull Map<String, String> labels,
      @Nullable String traceId
    ) {
      this.severity = severity;
      this.message = message;
      this.traceId = traceId;
      this.labels = labels;
    }
  }

  /**
   * Logger for operations that run in the context of
   * the application.
   */
  static class ApplicationContextLogger extends StructuredLogger {

    ApplicationContextLogger(@NotNull Appendable output) {
      super(output);
    }

    @Override
    public void addLabel(@NotNull String label, @NotNull String value) {
      //
      // This logger isn't scoped to any particular request or lifetime,
      // so adding labels might have unexpected effects.
      //
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Logger for operations that run in the context of
   * an environment.
   */
  static class EnvironmentContextLogger extends StructuredLogger {
    private final @NotNull String environmentName;

    EnvironmentContextLogger(
      @NotNull Appendable output,
      @NotNull String environmentName) {
      super(output);
      this.environmentName = environmentName;
    }

    @Override
    protected @NotNull Map<String, String> createLabels(String eventId) {
      var labels = super.createLabels(eventId);
      labels.put("environment", this.environmentName);
      return labels;
    }
  }

  /**
   * Logger for operations that run in the context of
   * a user request.
   */
  static class RequestContextLogger extends StructuredLogger {
    private final @NotNull RequestContext requestContext;
    private @Nullable String traceId;

    RequestContextLogger(
      @NotNull Appendable output,
      @NotNull RequestContext requestContext
    ) {
      super(output);
      this.requestContext = requestContext;
    }

    RequestContextLogger(@NotNull RequestContext requestContext) {
      this(System.out, requestContext);
    }

    @Override
    protected @Nullable String traceId() {
      return this.requestContext.requestTraceId();
    }

    @Override
    protected @NotNull Map<String, String> createLabels(String eventId) {
      var labels = super.createLabels(eventId);

      if (this.requestContext.isAuthenticated()) {
        labels.put("user_id", requestContext.user().email);
        labels.put("device_id", requestContext.device().deviceId());
        labels.put("device_access_levels",
          String.join(", ", requestContext.device().accessLevels()));
      }

      labels.put("request_method", requestContext.requestMethod());
      labels.put("request_path", requestContext.requestPath());

      return labels;
    }
  }
}
