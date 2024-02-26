package com.google.solutions.jitaccess.core.catalog.group;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ActivationRequest;
import com.google.solutions.jitaccess.core.catalog.EntitlementCatalog;
import com.google.solutions.jitaccess.core.catalog.MpaActivationRequest;

import java.io.IOException;

/**
 * Catalog for groups.
 *
 * The catalog is scoped to a Google Cloud organization (or technically,
 * to a Cloud Identity/Workspace account).
 */
//public class GroupCatalog implements EntitlementCatalog<GroupMembership> {
//  @Override
//  public void verifyUserCanRequest(
//    @NotNull ActivationRequest<GroupMembership> request
//  ) throws AccessException, IOException {
//    throw new RuntimeException("NIY!");
//  }
//
//  @Override
//  public void verifyUserCanApprove(
//    @NotNull UserEmail approvingUser,
//    @NotNull MpaActivationRequest<GroupMembership> request
//  ) throws AccessException, IOException {
//    throw new RuntimeException("NIY!");
//  }
//}
