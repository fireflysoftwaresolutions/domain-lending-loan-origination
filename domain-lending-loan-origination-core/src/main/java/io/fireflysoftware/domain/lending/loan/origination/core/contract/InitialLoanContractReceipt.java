/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.time.Instant;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;
/** The immutable Core Contract link; no Loan mutation or contract activation is implied. */
public record InitialLoanContractReceipt(@NotNull UUID actionId,@NotNull UUID applicationId,@NotNull UUID resourceTenantId,@NotNull UUID partyId,
        @NotNull UUID roleInContractId,@NotNull UUID contractId,@NotNull UUID contractPartyId,@NotNull UUID productId,@NotNull String currencyCode,
        @NotNull String purpose,@NotNull String contractStatusAtLink,boolean contractCreated,
        @Schema(ref="#/components/schemas/FireflyInstant",requiredMode=Schema.RequiredMode.REQUIRED) Instant linkedAt,
        @NotNull String intentDigest,boolean replayed) {
    public InitialLoanContractReceipt {
        for(UUID id:List.of(actionId,applicationId,resourceTenantId,partyId,roleInContractId,contractId,contractPartyId,productId))Ids.require(id);
        if(currencyCode==null||!currencyCode.matches("[A-Z]{3}")||!"INITIAL_PRIMARY_LOAN_CONTRACT".equals(purpose)||!Set.of("DRAFT","ACTIVE").contains(contractStatusAtLink)||linkedAt==null)throw new IllegalArgumentException("Exact typed initial contract receipt required");Ids.digest(intentDigest);
    }
    public InitialLoanContractReceipt asReplay(){return new InitialLoanContractReceipt(actionId,applicationId,resourceTenantId,partyId,roleInContractId,contractId,contractPartyId,productId,currencyCode,purpose,contractStatusAtLink,contractCreated,linkedAt,intentDigest,true);}
    @Override public String toString(){return "InitialLoanContractReceipt[redacted]";}
}
