package com.google.solutions.jitaccess.core.catalog.group;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TimeSpan;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.policy.Policy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * Catalog for groups.
 *
 * The catalog is scoped to a Google Cloud organization (or technically,
 * to a Cloud Identity/Workspace account).
 */
public class GroupCatalog implements EntitlementCatalog<GroupMembership, OrganizationId> {
  /**
   * Pseudo-organization ID indicating the "current" organization.
   * Using the real ID would be unnecessary as this catalog only ever
   * operates on a single organization.
   */
  public static final OrganizationId CURRENT_ORGANIZATION = new OrganizationId("-");

  private final @NotNull List<Policy> policies;

  public GroupCatalog(@NotNull List<Policy> policies) {
    this.policies = policies;
  }

  private GroupEmail emailForEntitlement(Policy.Entitlement e) {

    // TODO: search groups
    throw new RuntimeException("NIY!");
  }

  private PrincipalSet createUserPrincipalSet() {

    // TODO: search groups
    throw new RuntimeException("NIY!");
  }

  //---------------------------------------------------------------------------
  // EntitlementCatalog.
  //---------------------------------------------------------------------------

  @Override
  public SortedSet<OrganizationId> listScopes(
    @NotNull UserEmail user
  ) {
    return new TreeSet<>(List.of(CURRENT_ORGANIZATION));
  }

  @Override
  public void verifyUserCanRequest(
    @NotNull ActivationRequest<GroupMembership> request
  ) throws AccessException, IOException {

    // TODO: analyze policy
    throw new RuntimeException("NIY!");
  }

  @Override
  public void verifyUserCanApprove(
    @NotNull UserEmail approvingUser,
    @NotNull MpaActivationRequest<GroupMembership> request
  ) throws AccessException, IOException {

    // TODO: analyze policy
    throw new RuntimeException("NIY!");
  }

  @Override
  public SortedSet<UserEmail> listReviewers(
    @NotNull UserEmail requestingUser,
    @NotNull GroupMembership entitlement
  ) throws AccessException, IOException {

    // TODO: analyze policy
    throw new RuntimeException("NIY!");
  }

  @Override
  public EntitlementSet<GroupMembership> listEntitlements(
    @NotNull UserEmail user,
    @NotNull OrganizationId scope
  ) throws AccessException, IOException {
    Preconditions.checkArgument(CURRENT_ORGANIZATION.equals(scope));

    var userPrincipalSet = createUserPrincipalSet();

    //
    // Find entitlements that list this user (or one of its groups)
    // as being eligible.
    //
    var availableEntitlements = new TreeSet<Entitlement<GroupMembership>>();
    for (var policyEntitlement : this.policies
      .stream()
      .flatMap(p -> p.entitlements().stream())
      .filter(e -> userPrincipalSet.overlaps(e.eligiblePrincipals()))
      .toList()) {

      //
      // Map entitlement to group and check if the user is already
      // a member of that group. If so, they must have activated
      // the entitlement already.
      //
      var group = emailForEntitlement(policyEntitlement);
      if (userPrincipalSet.contains(group)) {
        // TODO: find expiry
        TimeSpan validity = null;

        availableEntitlements.add(new Entitlement<>(
          new GroupMembership(group),
          policyEntitlement.name(), //TODO: include policy name
          policyEntitlement.approvalRequirement().activationType(),
          Entitlement.Status.ACTIVE,
          validity));
      }
      else {
        availableEntitlements.add(new Entitlement<>(
          new GroupMembership(group),
          policyEntitlement.name(), //TODO: include policy name
          policyEntitlement.approvalRequirement().activationType(),
          Entitlement.Status.AVAILABLE));
      }

      // TODO: does the API return expired memberships?
    }

    return new EntitlementSet<>(availableEntitlements, new TreeSet<>(), Set.of());
  }
}
