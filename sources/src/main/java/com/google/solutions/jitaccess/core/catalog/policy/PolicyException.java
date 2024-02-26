package com.google.solutions.jitaccess.core.catalog.policy;

import org.jetbrains.annotations.NotNull;

public class PolicyException extends Exception {
  private final @NotNull PolicyValidationIssues issues;

  public @NotNull PolicyValidationIssues getIssues() {
    return issues;
  }

  public PolicyException(
    @NotNull String message,
    @NotNull PolicyValidationIssues issues
  ) {
    super(message);
    this.issues = issues;
  }
}
