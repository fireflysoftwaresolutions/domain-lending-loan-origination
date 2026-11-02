/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.validation.impl.BoundedManagedEvidenceContentValidatorImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Input validation and real JSON codec only; no owner invocation or custody proof. */
class ManagedEvidenceInputContractTest {
    @Test void decodedByteConstraintRejectsNullEmptyAndOversizeButAcceptsExactLimit(){var validator=new BoundedManagedEvidenceContentValidatorImpl();assertThat(validator.isValid(null,null)).isFalse();assertThat(validator.isValid(new byte[0],null)).isFalse();assertThat(validator.isValid(new byte[]{1},null)).isTrue();assertThat(validator.isValid(new byte[2097152],null)).isTrue();assertThat(validator.isValid(new byte[2097153],null)).isFalse();}
    @Test void filename200AndDecodedBoundarySurviveValidationAndActualJsonRoundTrip()throws Exception{
        byte[] bytes=new byte[2097152];Arrays.fill(bytes,(byte)0xa5);var request=new ManagedEvidenceUploadRequest(UUID.randomUUID(),"a".repeat(200),"IDENTITY","application/pdf",bytes);
        try(var factory=Validation.buildDefaultValidatorFactory()){assertThat(factory.getValidator().validate(request)).isEmpty();assertThat(factory.getValidator().validate(new ManagedEvidenceUploadRequest(request.actionId(),"a".repeat(201),request.documentTypeCode(),request.mediaType(),bytes))).isNotEmpty();}
        var mapper=new ObjectMapper();byte[] encoded=mapper.writeValueAsBytes(request);assertThat(encoded.length).isLessThan(3145728);assertThat(mapper.readTree(encoded).get("content").textValue()).hasSize(2796204);var decoded=mapper.readValue(encoded,ManagedEvidenceUploadRequest.class);assertThat(decoded.content()).isEqualTo(bytes);decoded.content()[0]=0;assertThat(decoded.content()).isEqualTo(bytes);
    }
}
