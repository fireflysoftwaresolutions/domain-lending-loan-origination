/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.controller;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.DraftLoanIntakeService;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/loan-application-intakes")
@Tag(name="DraftLoanIntakes")
@SecurityScheme(name="lendingDomainIntakeBearer",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
@SecurityRequirement(name="lendingDomainIntakeBearer")
public final class DraftLoanIntakeController {
    private final DraftLoanIntakeService service;
    public DraftLoanIntakeController(DraftLoanIntakeService service){this.service=service;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @DisableIdempotency
    @Operation(operationId="createDraftLoanIntake",summary="Create or replay an authorized DRAFT loan intake",
        description="Preserves exact retained simulation terms. The receipt contains the initial DRAFT/PENDING_DOCUMENTS status, not an offer, contract, approval, or disbursement.")
    @ApiResponses({
        @ApiResponse(responseCode="201",description="Original draft application and primary borrower created",
            content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=DraftLoanIntakeReceipt.class))),
        @ApiResponse(responseCode="200",description="Exact original draft intake replayed",
            content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=DraftLoanIntakeReceipt.class)))
    })
    public Mono<ResponseEntity<DraftLoanIntakeReceipt>> create(@Valid @RequestBody DraftLoanIntakeRequest request){
        return service.create(request).map(value->ResponseEntity.status(value.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(value));
    }
}
