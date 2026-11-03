/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.math.BigDecimal;
import java.util.*;
/** Current ownership plus original terms read from the Loan owner, not a client projection. */
public record InitialLoanContractBasis(UUID applicationId,UUID resourceTenantId,UUID partyId,UUID primaryApplicantId,
        UUID simulationId,UUID productId,BigDecimal requestedAmount,int term,String currencyCode,String intakeIntentDigest) {
    public InitialLoanContractBasis {
        for(UUID id:List.of(applicationId,resourceTenantId,partyId,primaryApplicantId,simulationId,productId))Ids.require(id);
        if(requestedAmount==null||requestedAmount.signum()<=0||term<1||currencyCode==null||!currencyCode.matches("[A-Z]{3}"))throw new IllegalArgumentException("Actual persisted Loan terms required");Ids.digest(intakeIntentDigest);
    }
    @Override public String toString(){return "InitialLoanContractBasis[redacted]";}
}
