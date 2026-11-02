/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import java.util.*;
/** Only an exact current owner conflict or fully observed immutable intent mismatch can create this type. */
public final class ManagedEvidenceConflict extends BusinessException {
    private final UUID actionId;
    public ManagedEvidenceConflict(UUID actionId){super(HttpStatus.CONFLICT,"MANAGED_EVIDENCE_ACTION_CONFLICT","The original action is bound to different evidence");this.actionId=Objects.requireNonNull(actionId);}
    public UUID actionId(){return actionId;}
}
