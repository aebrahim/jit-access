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

package com.google.solutions.jitaccess.core.catalog.policy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.solutions.jitaccess.core.GroupEmail;
import com.google.solutions.jitaccess.core.UserEmail;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parsed representation of a JSON-formatted policy file.
 */
public class PolicyFile {
  private static Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9\\-_]{1,32}$");

  /**
   * The root node of the JSON.
   */
  private final @NotNull PolicyNode node;

  /**
   * Non-critical warnings.
   */
  private final @NotNull List<PolicyIssue> warnings;

  private PolicyFile(
    @Nullable PolicyNode node,
    @NotNull List<PolicyIssue> warnings
  ) {
    this.node = node;
    this.warnings = warnings;
  }

  /**
   * Parse a JSON-formatted policy file.
   */
  public static PolicyFile fromString(String json) throws PolicyException {
    var issueCollector = new IssueCollector();
    try {
      //
      // Parse the JSON.
      //
      var node = new ObjectMapper().readValue(json, PolicyNode.class);

      //
      // The policy is syntactically correct. Now check semantics.
      //
      node.validate(issueCollector);

      if (issueCollector.getIssues().stream().anyMatch(i -> i.error())) {
        return new PolicyFile(node, issueCollector.getIssues());
      }
      else {
        throw new PolicyException(
          "The policy did not pass validation",
          issueCollector.getIssues());
      }
    }
    catch (JsonProcessingException e) {
      issueCollector.add(true, PolicyIssue.Code.FILE_INVALID_SYNTAX, e.getMessage());

      throw new PolicyException(
        "Parsing the policy failed because it contains syntax errors",
        issueCollector.getIssues());
    }
  }

  private static class IssueCollector {
    private final @NotNull List<PolicyIssue> issues = new LinkedList<>();
    private @NotNull String currentContext = "file";

    void setContext(@NotNull String context) {
      this.currentContext = context;
    }

    List<PolicyIssue> getIssues() {
      return issues;
    }

    PolicyIssue add(
      boolean error,
      @NotNull PolicyIssue.Code code,
      @NotNull String format,
      Object... args
    ) {
      var description = new Formatter()
        .format(format, args)
        .toString();

      return new PolicyIssue(
        error,
        code,
        String.format("[%s]", this.currentContext, description));
    }
  }

  //---------------------------------------------------------------------------
  // Serialization.
  //---------------------------------------------------------------------------

  record PolicyNode(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("entitlements") List<EntitlementsNode> entitlements
  ) {
    void validate(IssueCollector issues) {
      if (this.id == null || !ID_PATTERN.matcher(this.id).matches()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_INVALID_ID,
          "'%s' is not a valid policy ID", this.id);
      }

      issues.setContext(this.id);

      if (this.name() == null || this.name().isBlank()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_MISSING_NAME,
          "The policy must have a name");
      }

      if (this.entitlements == null || this.entitlements.isEmpty()) {
        issues.add(
          true,
          PolicyIssue.Code.POLICY_MISSING_ENTITLEMENTS,
          "The policy must contain at least one entitlement");
      }

      this.entitlements.stream().forEach(e ->  e.validate(this, issues));
    }
  }

  record EntitlementsNode(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("expires_after") String expiry,
    @JsonProperty("eligible") EligibleNode eligible,
    @JsonProperty("approval") ApprovalNode approval
  ) {
    void validate(PolicyNode parent, IssueCollector issues) {
      if (this.id == null || !ID_PATTERN.matcher(this.id).matches()) {
        issues.add(
          true,
          PolicyIssue.Code.ENTITLEMENT_INVALID_ID,
          "'%s' is not a valid entitlement ID", this.id);
      }

      issues.setContext(String.format("%s > %s", parent.id, this.id));

      if (this.name() == null || this.name().isBlank()) {
        issues.add(
          true,
          PolicyIssue.Code.ENTITLEMENT_MISSING_NAME,
          "The entitlement must have a name", this.id);
      }

      try {
        Duration.parse(this.expiry);
      }
      catch (DateTimeParseException e) {
        issues.add(
          true,
          PolicyIssue.Code.ENTITLEMENT_INVALID_EXPIRY,
          "The expiry is invalid: %s", e.getMessage());
      }

      if (this.eligible == null ||
          this.eligible.principals == null ||
          this.eligible.principals.isEmpty()) {
        issues.add(
          false,
          PolicyIssue.Code.ENTITLEMENT_MISSING_ELIGIBLE_PRINCIPALS,
          "The entitlement does not contain any eligible principals");
      }

      this.eligible.validate(issues);
    }
  }

  record EligibleNode(
    @JsonProperty("principals") List<String> principals
  ) {
    void validate(IssueCollector issues) {
      for (var principal : principals) {
        if (!principal.startsWith(UserEmail.TYPE) &&
            !principal.startsWith(GroupEmail.TYPE)) {
          issues.add(
            true,
            PolicyIssue.Code.PRINCIPAL_INVALID,
            "'%s' is not a valid principal identifier, see " +
            "https://cloud.google.com/iam/docs/principal-identifiers for details",
            principal);
        }
      }
    }
  }

  record ApprovalNode(
    @JsonProperty("self") boolean self,
    @JsonProperty("peer") ApprovableByPeer peer
  ) {
    void validate(IssueCollector issues) {
//      if (this.self) {
//        if (this.peer != null) {
//          issues.addError("Self-approval and peer-approval are mutually exclusive");
//        }
//      }
//      else if (this.peer != null) {
//
//      }
//      else {
//        issues.addError("Approval settings are missing");
//      }
    }
  }

  record ApprovableByPeer(
    @JsonProperty("minimum_peers_to_notify") int minNumberOfPeers,
    @JsonProperty("maximum_peers_to_notify") int maxNumberOfPeers
  ) {}
}
