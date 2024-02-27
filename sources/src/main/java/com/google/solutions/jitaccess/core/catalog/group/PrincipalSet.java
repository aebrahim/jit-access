package com.google.solutions.jitaccess.core.catalog.group;

import com.google.solutions.jitaccess.core.PrincipalIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

class PrincipalSet {
  private final @NotNull Set<PrincipalIdentifier> principals;

  PrincipalSet(@NotNull Set<PrincipalIdentifier> principals) {
    this.principals = principals;
  }

  boolean contains(PrincipalIdentifier principal) { // TODO: test
    return this.principals.contains(principal);
  }

  /**
   * Check if two sets overlap.
   */
  boolean overlaps(@NotNull Set<PrincipalIdentifier> principals) {
    return this.principals.stream().anyMatch(id -> principals.contains(id));
  }

  /**
   * Check if two sets overlap.
   */
  boolean overlaps(@NotNull PrincipalSet set) {
    return overlaps(set.principals);
  }
}
