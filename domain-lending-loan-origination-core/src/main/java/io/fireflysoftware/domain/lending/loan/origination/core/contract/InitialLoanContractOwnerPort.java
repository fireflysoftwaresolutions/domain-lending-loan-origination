/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.util.*;
import reactor.core.publisher.Mono;
public interface InitialLoanContractOwnerPort {
    Mono<InitialLoanContractReceipt> link(InitialLoanContractAuthority.Scope original,InitialLoanContractBasis basis,UUID existingContractId);
    Mono<Optional<InitialLoanContractReceipt>> observe(InitialLoanContractAuthority.Scope original);
}
