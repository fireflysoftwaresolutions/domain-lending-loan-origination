/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
public record InitialLoanContractError(@NotNull UUID actionId,@NotNull String code,@NotNull Outcome outcome,@NotNull String recovery){
    @Schema(name="InitialPrimaryLoanContractOutcome") public enum Outcome{REFUSED,CONFLICT,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE,OBSERVATION_UNAVAILABLE}
}
