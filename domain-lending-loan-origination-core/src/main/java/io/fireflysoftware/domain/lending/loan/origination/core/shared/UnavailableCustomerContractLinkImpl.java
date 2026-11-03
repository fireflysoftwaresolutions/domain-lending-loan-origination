/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.shared;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
/** The legacy signature has no actual application/original action and cannot create or prove a contract link. */
public final class UnavailableCustomerContractLinkImpl implements CustomerContractLink {
    @Override public Mono<UUID> bind(UUID party,UUID contract,UUID product,String currency,UUID role){return Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Use the initial-primary contract operation with the actual existing application and original action"));}
}
