package com.google.solutions.jitaccess.web.defer;

import com.google.solutions.jitaccess.catalog.Deferral;
import com.google.solutions.jitaccess.catalog.JitGroupView;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.Property;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractDeferrer implements Deferrer {
  private final @NotNull TokenSigner tokenSigner;

  protected AbstractDeferrer(@NotNull TokenSigner tokenSigner) {
    this.tokenSigner = tokenSigner;
  }

  /**
   * Notify relevant users about a deferred join operation.
   * @param operation
   */
  abstract void onJoinOperationDeferred(@NotNull JitGroupView.JoinOperation operation);

  /**
   * Notify relevant users about the completion of a deferred join operation.
   */
  abstract void onDeferredJoinOperationCompleted();


  //---------------------------------------------------------------------------
  // Deferrer.
  //---------------------------------------------------------------------------

  @Override
  public @NotNull DeferralToken defer(JitGroupView.@NotNull JoinOperation op) {
    // encode claims
    // sign
    throw new UnsupportedOperationException("NIY");
  }

  @Override
  public @NotNull Deferral<JitGroupView.JoinOperation> pickup(DeferralToken token) {
    // verify
    // decode claims
    throw new UnsupportedOperationException("NIY");
  }

  private class DeferredJoin implements Deferral<JitGroupView.JoinOperation> {
    private final @NotNull UserId deferrer;
    private final @NotNull List<Property> input;

    DeferredJoin(@NotNull UserId deferrer, @NotNull List<Property> input) {
      this.deferrer = deferrer;
      this.input = input;
    }

    @Override
    public @NotNull UserId deferrer() {
      return this.deferrer;
    }

    @Override
    public @NotNull List<Property> input() {
      return this.input;
    }

    @Override
    public void onCompleted() {
      AbstractDeferrer.this.onDeferredJoinOperationCompleted();
    }
  }
}
