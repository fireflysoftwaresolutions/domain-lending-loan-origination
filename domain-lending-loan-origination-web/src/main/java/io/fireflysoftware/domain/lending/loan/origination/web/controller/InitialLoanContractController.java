/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.controller;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
@RestController
@RequestMapping("/api/v1/initial-primary-loan-contracts")
@SecurityRequirement(name="lendingDomainIntakeBearer")
public class InitialLoanContractController {
    private final ObjectProvider<InitialLoanContractAuthority> authorities;private final ObjectProvider<InitialLoanContractService> services;
    public InitialLoanContractController(ObjectProvider<InitialLoanContractAuthority> authorities,ObjectProvider<InitialLoanContractService> services){this.authorities=authorities;this.services=services;}
    @DisableIdempotency
    @PostMapping(path="/applications/{applicationId}/actions/{actionId}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="linkInitialPrimaryLoanContract",summary="Link the actual existing managed application to one retained initial primary Core Contract using the original action")
    @ApiResponses({@ApiResponse(responseCode="201",description="Core Contract initial link created; no Loan transition implied",content=@Content(schema=@Schema(implementation=InitialLoanContractReceipt.class))),@ApiResponse(responseCode="200",description="Exact original initial Core Contract link replayed",content=@Content(schema=@Schema(implementation=InitialLoanContractReceipt.class))),
        @ApiResponse(responseCode="400",description="Original path/action or request invalid",content=@Content),@ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current original party/actor authority refused before owner dispatch",content=@Content),
        @ApiResponse(responseCode="409",description="Original action or initial application purpose already linked differently",content=@Content(schema=@Schema(implementation=InitialLoanContractError.class))),@ApiResponse(responseCode="503",description="REFUSED before dispatch/proven rollback, OUTCOME_UNKNOWN, or COMMITTED_RESPONSE_UNAVAILABLE; same-action recovery",content=@Content(schema=@Schema(implementation=InitialLoanContractError.class)))})
    public Mono<ResponseEntity<InitialLoanContractReceipt>> link(@PathVariable UUID applicationId,@PathVariable UUID actionId,@Valid @RequestBody InitialLoanContractRequest request){return Mono.defer(()->{
        var invoked=new AtomicBoolean();
        return Mono.defer(()->{
        if(request==null||!actionId.equals(request.actionId()))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact original contract action required"));
        try{var a=authorities.getIfUnique();var s=services.getIfUnique();if(a==null||s==null)return Mono.error(new InitialLoanContractFailure(actionId,InitialLoanContractFailure.Outcome.REFUSED));
            return a.invoke(applicationId,actionId,InitialLoanContractAuthority.Purpose.LINK,()->{invoked.set(true);return s.link(applicationId,request);}).map(value->ResponseEntity.status(value.replayed()?200:201).cacheControl(CacheControl.noStore()).body(value));
        }catch(RuntimeException setup){return Mono.error(new InitialLoanContractFailure(actionId,InitialLoanContractFailure.Outcome.REFUSED));}
        }).onErrorMap(error->{if(error instanceof InitialLoanContractFailure||error instanceof InitialLoanContractPreworkRefusal)return error;if(!invoked.get()&&error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;return new InitialLoanContractFailure(actionId,invoked.get()?InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN:InitialLoanContractFailure.Outcome.REFUSED);});
    });}
    @GetMapping(path="/applications/{applicationId}/actions/{actionId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="observeInitialPrimaryLoanContract",summary="Read the original immutable Core Contract link under current original Loan actor/party authority; never creates a missing link")
    @ApiResponses({@ApiResponse(responseCode="200",description="Original Core Contract link independently observed",content=@Content(schema=@Schema(implementation=InitialLoanContractReceipt.class))),@ApiResponse(responseCode="204",description="No retained original Core Contract action observed; not proof that an in-flight action cannot commit",content=@Content),
        @ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current original party/actor authority refused",content=@Content),@ApiResponse(responseCode="503",description="OBSERVATION_UNAVAILABLE; no write issued",content=@Content(schema=@Schema(implementation=InitialLoanContractError.class)))})
    public Mono<ResponseEntity<InitialLoanContractReceipt>> observe(@PathVariable UUID applicationId,@PathVariable UUID actionId){return Mono.defer(()->{
        try{var a=authorities.getIfUnique();var s=services.getIfUnique();if(a==null||s==null)return Mono.error(new InitialLoanContractFailure(actionId,InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE));
            return a.invoke(applicationId,actionId,InitialLoanContractAuthority.Purpose.OBSERVE,()->s.observe(applicationId,actionId)).map(found->found.map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value)).orElseGet(()->ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()));
        }catch(RuntimeException setup){return Mono.error(new InitialLoanContractFailure(actionId,InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE));}
    }).onErrorMap(error->{if(error instanceof InitialLoanContractFailure)return error;if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return error;return new InitialLoanContractFailure(actionId,InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE);});}
    @ExceptionHandler(InitialLoanContractFailure.class) public ResponseEntity<InitialLoanContractError> unavailable(InitialLoanContractFailure error){return ResponseEntity.status(error.outcome()==InitialLoanContractFailure.Outcome.CONFLICT?409:503).cacheControl(CacheControl.noStore()).body(new InitialLoanContractError(error.actionId(),error.getCode(),InitialLoanContractError.Outcome.valueOf(error.outcome().name()),"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
}
