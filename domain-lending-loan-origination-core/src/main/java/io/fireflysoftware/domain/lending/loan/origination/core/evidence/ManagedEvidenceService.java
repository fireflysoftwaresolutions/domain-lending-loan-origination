/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;
import java.util.UUID;
import reactor.core.publisher.Mono;
public interface ManagedEvidenceService {
    Mono<ManagedEvidenceReceipt> upload(UUID applicationId,ManagedEvidenceUploadRequest request);
    Mono<ManagedEvidenceReceipt> recover(UUID applicationId,UUID originalActionId);
}
