package com.google.solutions.jitaccess.core.catalog.group;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.OrganizationId;
import com.google.solutions.jitaccess.core.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ActivationRequest;
import com.google.solutions.jitaccess.core.catalog.EntitlementCatalog;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;
import com.google.solutions.jitaccess.core.catalog.MpaActivationRequest;
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

    // TODO: compare policy with direct group memberships
    throw new RuntimeException("NIY!");
  }
}
