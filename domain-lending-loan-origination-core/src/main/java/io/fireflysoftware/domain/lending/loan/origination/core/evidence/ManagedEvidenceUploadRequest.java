/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.validation.BoundedManagedEvidenceContent;
import java.util.UUID;

/** Application comes from the route; party, tenant and custody come from the authenticated owners. */
public record ManagedEvidenceUploadRequest(@NotNull UUID actionId,@NotBlank @Size(max=200) String fileName,
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,49}") String documentTypeCode,
        @NotBlank @Size(max=100) String mediaType,
        @NotNull @BoundedManagedEvidenceContent @Schema(type="string",format="byte",minLength=4,maxLength=2796204,description="Base64 of 1 through 2097152 decoded content bytes; the JSON envelope is bounded to 3145728 bytes") byte[] content) {
    public ManagedEvidenceUploadRequest {content=content==null?null:content.clone();}
    @Override public byte[] content(){return content==null?null:content.clone();}
    @Override public String toString(){return "ManagedEvidenceUploadRequest[redacted]";}
}
