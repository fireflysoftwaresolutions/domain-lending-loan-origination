/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Actual immutable content plus an observed Loan association; verification remains pending. */
public record ManagedEvidenceReceipt(UUID actionId,UUID applicationId,UUID partyId,UUID documentId,UUID associationId,
        String fileName,String documentTypeCode,String mediaType,long contentLength,String contentDigest,
        String documentIntentDigest,String associationIntentDigest,@Schema(ref="#/components/schemas/FireflyInstant") Instant storedAt,@Schema(ref="#/components/schemas/FireflyInstant") Instant attachedAt,String associationState,String verificationStatus,boolean replayed) {
    @Override public String toString(){return "ManagedEvidenceReceipt[redacted]";}
}
