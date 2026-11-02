/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.util.*;
import reactor.core.publisher.Mono;
public interface InitialLoanContractService {
    Mono<InitialLoanContractReceipt> link(UUID actualApplicationId,InitialLoanContractRequest original);
    Mono<Optional<InitialLoanContractReceipt>> observe(UUID actualApplicationId,UUID originalActionId);
}
