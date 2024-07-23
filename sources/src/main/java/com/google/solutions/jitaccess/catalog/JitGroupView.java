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

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.policy.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * JIT Group in the context of a specific subject.
 */
public class JitGroupView {
  private final @NotNull JitGroupPolicy policy;
  private final @NotNull Subject subject;
  private final @NotNull Provisioner provisioner;

  JitGroupView(
    @NotNull JitGroupPolicy policy,
    @NotNull Subject subject,
    @NotNull Provisioner provisioner
  ) {
    this.provisioner = provisioner;
    this.policy = policy;
    this.subject = subject;
  }

  /**
   * Get group policy.
   */
  public @NotNull JitGroupPolicy policy() {
    return this.policy;
  }

  /**
   * Resolve the Cloud Identity group that backs this JIT group.
   */
  public @NotNull GroupId cloudIdentityGroupId() {
    return this.provisioner.provisionedGroupId(this.policy());
  }

  /**
   * Get details about possibly unmet constraints.
   */
  public @NotNull JoinOperation join() {
    //
    // Check if the current subject can self-approve. If so, initiate a join-
    // operation with self-approval.
    //
    // NB. Self-approval requires that the subject also satisfies approval constraints.
    //
    var joinWithSelfApprovalAnalysis = policy
      .analyze(this.subject, EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF))
      .applyConstraints(Policy.ConstraintClass.JOIN)
      .applyConstraints(Policy.ConstraintClass.APPROVE);
    if (joinWithSelfApprovalAnalysis
      .execute()
      .isAccessAllowed(PolicyAnalysis.AccessOptions.IGNORE_CONSTRAINTS)) {
      //
      // Continue with self-approval.
      //
      return new JoinOperation(
        false,
        joinWithSelfApprovalAnalysis);
    }

    //
    // The current subject can't self-approve, but they might be allowed
    // to join with approval.
    //
    return new JoinOperation(
      true,
      policy
        .analyze(this.subject, EnumSet.of(PolicyPermission.JOIN))
        .applyConstraints(Policy.ConstraintClass.JOIN));
  }

  // public ApprovalOperation approve(@NotNull String token)
  //   verify different users

  public class JoinOperation {
    private final boolean requiresApproval;
    private final @NotNull PolicyAnalysis analysis;

    private JoinOperation(
      boolean requiresApproval,
      @NotNull PolicyAnalysis analysis
    ) {
      this.requiresApproval = requiresApproval;
      this.analysis = analysis;
    }

    /**
     * Indicates whether the operation requires approval.
     */
    public boolean requiresApproval() {
      return this.requiresApproval;
    }

    /**
     * @return input required to evaluate constraints.
     */
    public @NotNull List<Property> input() {
      return this.analysis.input();
    }

    /**
     * Perform a "dry run" to check if the join would succeed
     * given the current input.
     */
    public @NotNull PolicyAnalysis.Result dryRun() {
      //
      // Re-run analysis using the latest inputs.
      //
      return this.analysis.execute();
    }

    public @NotNull ApprovalOperation delegateForApproval() throws AccessException { // TODO: test
      if (!this.requiresApproval) {
        throw new AccessDeniedException("The join operation does not require approval");
      }

      //
      // Verify that access is granted and all constraints
      // are satisfied.
      //
      this.analysis
        .execute()
        .verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);

      return new ApprovalOperation(JitGroupView.this.subject);
    }

    /**
     * Perform the join.
     * @throws AccessException
     */
    public @NotNull Principal execute() throws AccessException, IOException {// TODO: test
      if (this.requiresApproval) {
        throw new AccessDeniedException("The join operation requires approval");
      }

      //
      // Verify that access is granted and all constraints
      // are satisfied.
      //
      var analysisResult = this.analysis.execute();
      analysisResult.verifyAccessAllowed(PolicyAnalysis.AccessOptions.DEFAULT);

      //
      // Extract expiry, which could be fixed or user-provided.
      //
      // NB. We verified all constraints, so if expiry is empty, then
      // there is no expiry constraint.
      //
      var expiry = analysisResult.satisfiedConstraints()
        .stream()
        .filter(c -> c instanceof ExpiryConstraint)
        .map(c -> (ExpiryConstraint)c)
        .flatMap(c -> c.extractExpiry(analysisResult).stream())
        .map(d -> Instant.now().plus(d))
        .findFirst()
        .orElseThrow(() -> new UnsupportedOperationException(
          String.format(
            "The group %s doesn't specify an expiry constraint",
            JitGroupView.this.policy().id())));

      //
      // Provision group membership.
      //
      var group = JitGroupView.this;
      group.provisioner.provisionMembership(
        group.policy,
        group.subject.user(),
        expiry);

      return new Principal(
        JitGroupView.this.policy.id(),
        expiry);
    }
  }

  public class ApprovalOperation {
    private final @NotNull Subject requestingSubject;

    public ApprovalOperation(@NotNull Subject requestingSubject) {
      this.requestingSubject = requestingSubject;
      throw new UnsupportedOperationException("Approval is not supported yet");
    }

    // requestingSubject
    // input
    // dryRun (w/ approver constraints)
    // execute ->
    // parse( token)
  }
}
