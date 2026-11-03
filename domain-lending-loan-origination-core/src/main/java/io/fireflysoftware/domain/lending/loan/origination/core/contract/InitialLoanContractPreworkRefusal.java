/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
/** Emitted only by orchestration before entering the Contract owner invocation. */
public final class InitialLoanContractPreworkRefusal extends ResponseStatusException {
    public InitialLoanContractPreworkRefusal(HttpStatusCode status){super(status,"Current initial loan contract authority refused before owner dispatch");if(status.value()!=400&&status.value()!=401&&status.value()!=403)throw new IllegalArgumentException("Exact prework refusal status required");}
}
