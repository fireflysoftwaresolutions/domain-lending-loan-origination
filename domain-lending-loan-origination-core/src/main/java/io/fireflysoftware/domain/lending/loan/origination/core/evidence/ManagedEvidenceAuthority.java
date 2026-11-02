/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;

import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

/** Implemented only by the current verified-JWT/policy context bridge, not request data. */
public interface ManagedEvidenceAuthority {
    record Scope(UUID actionId,UUID applicationId,UUID partyId,UUID loanResourceTenantId,UUID documentResourceTenantId,
            String issuer,String subject,String platformTenant,Instant observedAt,long observedNanos,Instant notAfter) {
        public Scope {
            for(UUID id:new UUID[]{actionId,applicationId,partyId,loanResourceTenantId,documentResourceTenantId})
                if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact managed evidence identity required");
            for(String text:new String[]{issuer,subject,platformTenant})if(text==null||text.isBlank())throw new IllegalArgumentException("Current opaque HUMAN identity required");
            if(observedAt==null||notAfter==null||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)
                throw new IllegalArgumentException("Original finite evidence lease required");
        }
        @Override public String toString(){return "ManagedEvidenceScope[redacted]";}
    }
    Mono<Scope> requireCurrent(UUID applicationId,UUID originalActionId);
    Mono<Void> revalidate(Scope scope);
}
