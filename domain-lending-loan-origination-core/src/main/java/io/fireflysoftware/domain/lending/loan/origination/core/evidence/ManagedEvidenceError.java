/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;
import java.util.UUID;
/** Known owner identifiers are omitted if current authority cannot be revalidated. */
public record ManagedEvidenceError(UUID actionId,String code,Outcome outcome,String recovery,UUID documentId,UUID associationId,
        String contentDigest,String documentIntentDigest){
    public enum Outcome { CONFLICT,OBSERVATION_UNAVAILABLE,DOCUMENT_OUTCOME_UNKNOWN,DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE,
        DOCUMENT_STORED_ASSOCIATION_UNKNOWN,ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE }
    @Override public String toString(){return "ManagedEvidenceError[redacted]";}
}
