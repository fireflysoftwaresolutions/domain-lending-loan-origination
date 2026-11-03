/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.validation;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.validation.impl.BoundedManagedEvidenceContentValidatorImpl;
import jakarta.validation.*;
import java.lang.annotation.*;
/** Decoded byte bounds are independent of the base64 wire length. */
@Documented
@Constraint(validatedBy=BoundedManagedEvidenceContentValidatorImpl.class)
@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BoundedManagedEvidenceContent {
    int MAX_BYTES=2097152;
    String message() default "Content must contain 1 through 2097152 decoded bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
