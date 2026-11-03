/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.validation.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.validation.BoundedManagedEvidenceContent;
import jakarta.validation.*;
public final class BoundedManagedEvidenceContentValidatorImpl implements ConstraintValidator<BoundedManagedEvidenceContent,byte[]> {
    @Override public boolean isValid(byte[] value,ConstraintValidatorContext context){return value!=null&&value.length>=1&&value.length<=BoundedManagedEvidenceContent.MAX_BYTES;}
}
