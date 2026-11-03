/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.intake;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.*;
import reactor.core.publisher.Mono;
public interface DraftLoanIntakeService {
    Mono<DraftLoanIntakeReceipt> create(DraftLoanIntakeRequest request);
}
