/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;
public interface InitialLoanContractAuthority {
    enum Purpose {LINK,OBSERVE}
    static String action(Purpose purpose){return purpose==Purpose.LINK?"lending:applications:initial-primary-contract:link":"lending:applications:initial-primary-contract:read";}
    <T> Mono<T> invoke(UUID applicationId,UUID actionId,Purpose purpose,java.util.function.Supplier<Mono<T>> operation);
    record Scope(UUID actionId,UUID applicationId,UUID partyId,UUID loanResourceTenantId,UUID contractResourceTenantId,
            String issuer,String subject,String platformTenant,Purpose purpose,Instant observedAt,long observedNanos,Instant notAfter) {
        public Scope {
            for(UUID id:List.of(actionId,applicationId,partyId,loanResourceTenantId,contractResourceTenantId))Ids.require(id);
            for(String value:List.of(issuer,subject,platformTenant))if(value.isBlank())throw new IllegalArgumentException("Verified original identity required");Objects.requireNonNull(purpose);
            if(observedAt==null||notAfter==null||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)throw new IllegalArgumentException("Original finite authority window required");
        }
        @Override public String toString(){return "InitialContractScope[redacted]";}
    }
    Mono<Scope> requireCurrent(UUID applicationId,UUID actionId,Purpose purpose);
    Mono<Void> revalidate(Scope original);
    Mono<Void> requireTransportContext(Scope original);
}
