/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
/** Product, currency, party, tenant and role come from current owning services. */
public record InitialLoanContractRequest(@NotNull UUID actionId,UUID existingContractId) {
    public InitialLoanContractRequest {Ids.require(actionId);if(existingContractId!=null)Ids.require(existingContractId);}
}
