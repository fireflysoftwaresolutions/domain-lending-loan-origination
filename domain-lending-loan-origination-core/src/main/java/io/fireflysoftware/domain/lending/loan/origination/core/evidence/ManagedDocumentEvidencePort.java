/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;

import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;

/** Actual adapter must use the owning generated Document SDK and its bounded binary reader. */
public interface ManagedDocumentEvidencePort {
    record Stored(UUID actionId,UUID applicationId,UUID partyId,UUID resourceTenantId,UUID documentId,
            String fileName,String documentTypeCode,String mediaType,long contentLength,String contentDigest,
            String ownerIntentDigest,Instant storedAt,boolean replayed) {
        @Override public String toString(){return "StoredDocumentEvidence[redacted]";}
    }
    record Content(UUID actionId,UUID documentId,long contentLength,String contentDigest) {}
    enum CustodyOutcome { PROVEN_REFUSED,OUTCOME_UNKNOWN,COMMITTED }
    /** Transport-local fact: the adapter must prove no dispatch or validate an exact typed owner response/receipt. */
    final class OutcomeFailure extends RuntimeException {
        private final UUID action,application,party,tenant;private final CustodyOutcome outcome;private final Stored known;
        public OutcomeFailure(ManagedEvidenceAuthority.Scope scope,CustodyOutcome outcome,Stored known){
            super("Original document custody response unavailable");Objects.requireNonNull(scope);this.action=scope.actionId();this.application=scope.applicationId();this.party=scope.partyId();this.tenant=scope.documentResourceTenantId();this.outcome=Objects.requireNonNull(outcome);this.known=known;
            if(known!=null&&(outcome!=CustodyOutcome.COMMITTED||!action.equals(known.actionId())||!application.equals(known.applicationId())||!party.equals(known.partyId())||!tenant.equals(known.resourceTenantId())))throw new IllegalArgumentException("Exact scoped original owner receipt required");
        }
        public boolean matches(ManagedEvidenceAuthority.Scope scope){return action.equals(scope.actionId())&&application.equals(scope.applicationId())&&party.equals(scope.partyId())&&tenant.equals(scope.documentResourceTenantId());}
        public CustodyOutcome outcome(){return outcome;}public Optional<Stored> known(){return Optional.ofNullable(known);}
        @Override public String toString(){return "DocumentCustodyOutcomeFailure[redacted]";}
    }

    Mono<Stored> store(ManagedEvidenceAuthority.Scope scope,ManagedEvidenceUploadRequest exactRequest);
    Mono<Optional<Stored>> observe(ManagedEvidenceAuthority.Scope scope);
    Mono<Content> readContent(ManagedEvidenceAuthority.Scope scope,Stored receipt);
}
