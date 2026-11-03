/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.intake.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.DraftLoanIntakeService;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
@Service
public final class DraftLoanIntakeServiceImpl implements DraftLoanIntakeService {
    private final ObjectProvider<CoreDraftIntakePort> owner;
    public DraftLoanIntakeServiceImpl(ObjectProvider<CoreDraftIntakePort> owner){this.owner=owner;}
    @Override public Mono<DraftLoanIntakeReceipt> create(DraftLoanIntakeRequest request){return Mono.defer(()->{
        var adapter=owner.getIfUnique();if(adapter==null)return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Authenticated lending intake owner unavailable"));
        return adapter.create(request).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Lending intake owner returned no receipt")));
    });}
}
