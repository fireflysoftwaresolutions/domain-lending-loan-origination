/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;

import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;

/** Only Loan Core persists the association and any explicit durable cancellation. */
public interface ManagedLoanEvidencePort {
    record Attached(UUID actionId,UUID applicationId,UUID partyId,UUID resourceTenantId,UUID associationId,
            UUID documentResourceTenantId,UUID documentId,UUID documentTypeId,String documentTypeCode,String fileName,String mediaType,
            long contentLength,String contentDigest,String documentIntentDigest,Instant documentStoredAt,String state,String verificationStatus,
            Instant attachedAt,boolean replayed,String ownerIntentDigest) {
        @Override public String toString(){return "AttachedDocumentEvidence[redacted]";}
    }
    enum AssociationOutcome { PROVEN_REFUSED,OUTCOME_UNKNOWN,COMMITTED }
    /** Transport-local knowledge must be scoped to the original action and both configured owner namespaces. */
    final class OutcomeFailure extends RuntimeException {
        private final UUID action,application,party,loanTenant,documentTenant;private final AssociationOutcome outcome;private final Attached known;
        public OutcomeFailure(ManagedEvidenceAuthority.Scope scope,AssociationOutcome outcome,Attached known){
            super("Original association response unavailable");Objects.requireNonNull(scope);action=scope.actionId();application=scope.applicationId();party=scope.partyId();loanTenant=scope.loanResourceTenantId();documentTenant=scope.documentResourceTenantId();this.outcome=Objects.requireNonNull(outcome);this.known=known;
            if(known!=null&&(outcome!=AssociationOutcome.COMMITTED||!action.equals(known.actionId())||!application.equals(known.applicationId())||!party.equals(known.partyId())||!loanTenant.equals(known.resourceTenantId())||!documentTenant.equals(known.documentResourceTenantId())))throw new IllegalArgumentException("Exact scoped original association required");
        }
        public boolean matches(ManagedEvidenceAuthority.Scope scope){return action.equals(scope.actionId())&&application.equals(scope.applicationId())&&party.equals(scope.partyId())&&loanTenant.equals(scope.loanResourceTenantId())&&documentTenant.equals(scope.documentResourceTenantId());}
        public AssociationOutcome outcome(){return outcome;}public Optional<Attached> known(){return Optional.ofNullable(known);}
        @Override public String toString(){return "AssociationOutcomeFailure[redacted]";}
    }
    Mono<Optional<Attached>> observe(ManagedEvidenceAuthority.Scope scope);
    Mono<Attached> attach(ManagedEvidenceAuthority.Scope scope,ManagedDocumentEvidencePort.Stored stored);
}
