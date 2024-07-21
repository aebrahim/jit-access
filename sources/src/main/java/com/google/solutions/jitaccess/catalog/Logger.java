package com.google.solutions.jitaccess.catalog;

import org.jetbrains.annotations.NotNull;

public interface Logger {
  /**
   * Log an informational event
   * @param eventId unique ID for the event
   * @param message formatted message.
   */
  void info(
    @NotNull String eventId,
    @NotNull String message);

  /**
   * Log an informational event
   * @param eventId unique ID for the event
   * @param format message format.
   */
  void info(
    @NotNull String eventId,
    @NotNull String format,
    Object... args);

  /**
   * Log a warning event
   * @param eventId unique ID for the event
   * @param message formatted message.
   */
  void warn(
    @NotNull String eventId,
    @NotNull String message);

  /**
   * Log a warning event
   * @param eventId unique ID for the event
   * @param format message format.
   */
  void warn(
    @NotNull String eventId,
    @NotNull String format,
    Object... args);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param message formatted message.
   * @param exception exception
   */
  void warn(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param message formatted message.
   */
  void error(
    @NotNull String eventId,
    @NotNull String message);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param format message format.
   */
  void error(
    @NotNull String eventId,
    @NotNull String format,
    Object... args);

  /**
   * Log an error event
   * @param eventId unique ID for the event
   * @param message formatted message.
   * @param exception exception
   */
  void error(
    @NotNull String eventId,
    @NotNull String message,
    @NotNull Exception exception);
}
