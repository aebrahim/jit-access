package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.web.RequireIapPrincipal;
import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Dependent
@Path("/api/catalog")
@RequireIapPrincipal
public class LintResource { // TODO: test
  /**
   * Validate policy document
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Path("lint")
  public @NotNull LintingResultInfo validatePolicy(
    @FormParam("source") @Nullable String source
  ) {

    try {
      PolicyDocument.fromString(
        source,
        new Policy.Metadata("user-provided", Instant.now()));
    }
    catch (PolicyDocument.SyntaxException e) {
      return LintingResultInfo.create(e);
    }

    return LintingResultInfo.SUCCESS;
  }

  //---------------------------------------------------------------------------
  // Payload records.
  //---------------------------------------------------------------------------

  public record LintingResultInfo(
    boolean successful,
    @NotNull List<IssueInfo> issues
    ) {
    static LintingResultInfo SUCCESS = new LintingResultInfo(true, List.of());

    static LintingResultInfo create(@NotNull PolicyDocument.SyntaxException e) {
      return new LintingResultInfo(
        false,
        e.issues()
          .stream()
          .map(IssueInfo::fromIssue)
          .toList());
    }
  }

  public record IssueInfo(
    boolean error,
    @NotNull String code,
    @NotNull String details
  ) {
    static IssueInfo fromIssue(@NotNull PolicyDocument.Issue issue) {
      return new IssueInfo(issue.error(), issue.code().toString(), issue.details());
    }
  }
}
