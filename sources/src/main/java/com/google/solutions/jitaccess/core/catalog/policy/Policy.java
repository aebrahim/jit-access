//package com.google.solutions.jitaccess.core.catalog.policy;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//import com.google.common.base.Preconditions;
//import com.google.solutions.jitaccess.core.GroupEmail;
//import com.google.solutions.jitaccess.core.PrincipalIdentifier;
//import com.google.solutions.jitaccess.core.UserEmail;
//import org.jetbrains.annotations.NotNull;
//
//import java.time.Duration;
//import java.time.format.DateTimeParseException;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Set;
//import java.util.regex.Pattern;
//
//public record Policy(
//  @NotNull String id,
//  @NotNull String name,
//  @NotNull List<Entitlement> entitlements
//) {
//  private static Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9\\-_]{1,32}$");
//
//  static Policy from(
//    @NotNull PolicyElement element
//  ) throws PolicyException {
//
//    Preconditions.checkNotNull(element, "element");
//
//    if (element.id == null || !ID_PATTERN.matcher(element.id).matches()) {
//      throw new InvalidPolicyException(
//        String.format("'%s' is not a valid policy ID", element.id));
//    }
//
//    if (element.name() == null || element.name().isBlank()) {
//      throw new InvalidPolicyException(
//        String.format("Policy '%s' must have a name", element.id));
//    }
//
//    if (element.entitlements == null || element.entitlements.isEmpty()) {
//      throw new InvalidPolicyException(
//        String.format("Policy '%s' must contain at least one entitlement", element.id));
//    }
//
//    var entitlements = new LinkedList<Entitlement>();
//    for (var entitlementElement : element.entitlements) {
//      entitlements.add(Entitlement.from(entitlementElement));
//    }
//
//    return new Policy(element.id, element.name, entitlements);
//  }
//
//  public record Entitlement(
//    @NotNull String id,
//    @NotNull String name,
//    @NotNull Duration expiry,
//    @NotNull PrincipalSet eligiblePrincipals
//  ) {
//    static Entitlement from(EntitlementsElement entitlementElement) throws PolicyException {
//      if (entitlementElement.id == null || !ID_PATTERN.matcher(entitlementElement.id).matches()) {
//        throw new InvalidPolicyException(
//          String.format("'%s' is not a valid entitlement ID", entitlementElement.id));
//      }
//
//      if (entitlementElement.name() == null || entitlementElement.name().isBlank()) {
//        throw new InvalidPolicyException(
//          String.format("Entitlement '%s' must have a name", entitlementElement.id));
//      }
//
//      try {
//        var expiry = Duration.parse(entitlementElement.expiry);
//        if (expiry.isZero() || expiry.isNegative()) {
//          throw new InvalidPolicyException(
//            String.format("Expiry for entitlement '%s' must be positive", entitlementElement.id));
//        }
//
//        if (entitlementElement.eligible == null ||
//          entitlementElement.eligible.principals == null ||
//          entitlementElement.eligible.principals.isEmpty()) {
//
//          throw new InvalidPolicyException(
//            String.format(
//              "The list of principals that can request entitlement '%s' is empty",
//              entitlementElement.id));
//        }
//
//        return new Entitlement(
//          entitlementElement.id,
//          entitlementElement.name,
//          expiry,
//          PrincipalSet.from(entitlementElement.eligible.principals));
//      }
//      catch (DateTimeParseException e) {
//        throw new InvalidPolicyException(
//          String.format("Expiry for entitlement '%s' is invalid", entitlementElement.id),
//          e);
//      }
//    }
//  }
//
//  public record PrincipalSet(
//    @NotNull Set<PrincipalIdentifier> principals
//  ) {
//    static PrincipalSet from(List<String> principalStrings) throws PolicyException {
//      var principals = new HashSet<PrincipalIdentifier>();
//      for (var s : principalStrings) {
//        if (s.startsWith(UserEmail.TYPE)) {
//          principals.add(new UserEmail(s.substring(UserEmail.TYPE.length())));
//        }
//        else if (s.startsWith(GroupEmail.TYPE)) {
//          principals.add(new GroupEmail(s.substring(GroupEmail.TYPE.length())));
//        }
//        else {
//         throw new InvalidPrincipalIdentifierException(s);
//        }
//      }
//
//      return new PrincipalSet(principals);
//    }
//  }
//
//
//
//  public static class InvalidPolicyException extends PolicyException {
//    public InvalidPolicyException(String message) {
//      super(message);
//    }
//
//    public InvalidPolicyException(String message, Throwable cause) {
//      super(message, cause);
//    }
//  }
//
//  public static class InvalidPrincipalIdentifierException extends PolicyException {
//    public InvalidPrincipalIdentifierException(String identifier) {
//      super(String.format(
//        "'%s' is not a valid principal identifier, see " +
//          "https://cloud.google.com/iam/docs/principal-identifiers for details",
//        identifier));
//    }
//  }
//}
