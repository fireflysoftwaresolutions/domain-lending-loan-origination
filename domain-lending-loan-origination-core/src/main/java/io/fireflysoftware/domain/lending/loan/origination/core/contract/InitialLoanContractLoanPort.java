/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import reactor.core.publisher.Mono;
@FunctionalInterface
public interface InitialLoanContractLoanPort {Mono<InitialLoanContractBasis> observe(InitialLoanContractAuthority.Scope original);}
