/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.controller;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl.AuthenticatedManagedEvidenceAuthorityImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/managed-loan-evidence")
@SecurityRequirement(name="lendingDomainIntakeBearer")
public final class ManagedLoanEvidenceController {
    private final ObjectProvider<AuthenticatedManagedEvidenceAuthorityImpl> authorities;private final ObjectProvider<ManagedEvidenceService> services;
    public ManagedLoanEvidenceController(ObjectProvider<AuthenticatedManagedEvidenceAuthorityImpl> authorities,ObjectProvider<ManagedEvidenceService> services){this.authorities=authorities;this.services=services;}
    @DisableIdempotency
    @PostMapping(path="/applications/{applicationId}/actions/{actionId}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="storeManagedLoanEvidence",summary="Store actual evidence bytes and attach the verified owner reference to the signed party's managed loan application",
        description="The route owns the application and action. Current signed authority owns the party and owner namespaces. The Document owner stores immutable bytes; the Loan owner alone persists the association. Verification remains PENDING. Exact replay always revalidates current authority.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Actual Document custody and original Loan association observed",content=@Content(schema=@Schema(implementation=ManagedEvidenceReceipt.class))),
        @ApiResponse(responseCode="200",description="Exact original evidence and association replayed",content=@Content(schema=@Schema(implementation=ManagedEvidenceReceipt.class))),
        @ApiResponse(responseCode="400",description="Invalid content, filename or original route/action",content={}),@ApiResponse(responseCode="413",description="JSON envelope exceeds 3145728 bytes",content={}),
        @ApiResponse(responseCode="401",description="Bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current original party authority refused",content={}),
        @ApiResponse(responseCode="409",description="Original action has different immutable evidence",content=@Content(schema=@Schema(implementation=ManagedEvidenceError.class))),
        @ApiResponse(responseCode="503",description="Typed original owner progress retained; recover the same action without deleting custody or starting a new action",content=@Content(schema=@Schema(implementation=ManagedEvidenceError.class)))})
    public Mono<ResponseEntity<ManagedEvidenceReceipt>> upload(@PathVariable UUID applicationId,@PathVariable UUID actionId,@Valid @RequestBody ManagedEvidenceUploadRequest request){
        if(request==null||!actionId.equals(request.actionId()))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact original route action required"));
        return invoke(applicationId,actionId,service->service.upload(applicationId,request)).map(receipt->ResponseEntity.status(receipt.replayed()?HttpStatus.OK:HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(receipt));
    }
    @DisableIdempotency
    @PostMapping(path="/applications/{applicationId}/actions/{actionId}/recovery",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="recoverManagedLoanEvidence",summary="Observe original Document bytes and complete or read the same original Loan association",
        description="May create the missing Loan association after revalidating current authority and hashing actual stored Document bytes. Never uploads another document, mints another action or deletes retained custody.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Original pending association completed",content=@Content(schema=@Schema(implementation=ManagedEvidenceReceipt.class))),
        @ApiResponse(responseCode="200",description="Existing exact original association and content verified",content=@Content(schema=@Schema(implementation=ManagedEvidenceReceipt.class))),
        @ApiResponse(responseCode="400",description="Invalid original application/action",content={}),@ApiResponse(responseCode="401",description="Bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current original party authority refused",content={}),
        @ApiResponse(responseCode="409",description="Original action is bound to different evidence",content=@Content(schema=@Schema(implementation=ManagedEvidenceError.class))),
        @ApiResponse(responseCode="503",description="Typed original owner progress retained; only same-action recovery is safe",content=@Content(schema=@Schema(implementation=ManagedEvidenceError.class)))})
    public Mono<ResponseEntity<ManagedEvidenceReceipt>> recover(@PathVariable UUID applicationId,@PathVariable UUID actionId){return invoke(applicationId,actionId,service->service.recover(applicationId,actionId)).map(receipt->ResponseEntity.status(receipt.replayed()?HttpStatus.OK:HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(receipt));}
    private Mono<ManagedEvidenceReceipt> invoke(UUID application,UUID action,java.util.function.Function<ManagedEvidenceService,Mono<ManagedEvidenceReceipt>> operation){return Mono.defer(()->{
        final AuthenticatedManagedEvidenceAuthorityImpl authority;final ManagedEvidenceService service;
        try{authority=authorities.getIfUnique();service=services.getIfUnique();}catch(RuntimeException unavailable){return Mono.error(unavailable(action));}
        if(authority==null||service==null)return Mono.error(unavailable(action));
        return authority.invoke(application,action,()->operation.apply(service)).onErrorMap(error->{if(error instanceof ManagedEvidenceFailure||error instanceof ManagedEvidenceConflict)return error;if(error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;return unavailable(action);});
    });}
    @ExceptionHandler(ManagedEvidenceFailure.class) public ResponseEntity<ManagedEvidenceError> unavailable(ManagedEvidenceFailure error){var details=error.getMetadata();return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new ManagedEvidenceError(error.actionId(),error.getCode(),ManagedEvidenceError.Outcome.valueOf(error.outcome().name()),"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY",id(details,"documentId"),id(details,"associationId"),text(details,"contentDigest"),text(details,"documentIntentDigest")));}
    @ExceptionHandler(ManagedEvidenceConflict.class) public ResponseEntity<ManagedEvidenceError> conflict(ManagedEvidenceConflict error){return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(new ManagedEvidenceError(error.actionId(),error.getCode(),ManagedEvidenceError.Outcome.CONFLICT,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY",null,null,null,null));}
    private static ManagedEvidenceFailure unavailable(UUID action){return new ManagedEvidenceFailure(action,ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE,Map.of());}
    private static UUID id(Map<String,Object> values,String key){return values.get(key) instanceof String value?UUID.fromString(value):null;}
    private static String text(Map<String,Object> values,String key){return values.get(key) instanceof String value?value:null;}
}
