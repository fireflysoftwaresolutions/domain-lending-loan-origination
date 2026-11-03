/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.shared;
import io.fireflysoftware.domain.lending.loan.origination.core.shared.impl.UnavailableCustomerContractLinkImpl;
import java.util.UUID;
import reactor.core.publisher.Mono;
/** Retained legacy signature only. It cannot establish a governed original-action contract link. */
@Deprecated
public final class SdkCustomerContractLinkImpl implements CustomerContractLink {
    public SdkCustomerContractLinkImpl(String customerBaseUrl,String contractBaseUrl){}
    @Override public Mono<UUID> bind(UUID party,UUID contract,UUID product,String currency,UUID role){return new UnavailableCustomerContractLinkImpl().bind(party,contract,product,currency,role);}
}
