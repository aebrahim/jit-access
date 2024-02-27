package com.google.solutions.jitaccess.core.catalog.group;

import com.google.solutions.jitaccess.core.GroupEmail;
import com.google.solutions.jitaccess.core.UserEmail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

public class TestPrincipalSet {
  @Test
  public void emptySet() {
    var set = new PrincipalSet(Set.of());
    assertFalse(set.overlaps(new PrincipalSet(Set.of())));
    assertFalse(set.overlaps(new PrincipalSet(Set.of(new UserEmail("bob@example.com")))));
  }

  @Test
  public void superset() {
    var set = new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new UserEmail("bob@example.com"),
      new GroupEmail("ftes@example.com")));

    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new UserEmail("bob@example.com"),
      new GroupEmail("ftes@example.com")))));
  }

  @Test
  public void subset() {
    var set = new PrincipalSet(Set.of(new UserEmail("alice@example.com")));

    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com")))));
    assertFalse(set.overlaps(new PrincipalSet(Set.of(
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new GroupEmail("ftes@example.com")))));
    assertTrue(set.overlaps(new PrincipalSet(Set.of(
      new UserEmail("alice@example.com"),
      new UserEmail("bob@example.com"),
      new GroupEmail("ftes@example.com")))));
  }
}
