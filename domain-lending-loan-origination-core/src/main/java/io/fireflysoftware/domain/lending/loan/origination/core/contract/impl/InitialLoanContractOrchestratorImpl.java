/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Coordinates actual owners only. Known Contract commit is never reclassified by a later read or policy failure. */
public final class InitialLoanContractOrchestratorImpl implements InitialLoanContractService {
    private final InitialLoanContractAuthority authority;private final InitialLoanContractLoanPort loans;private final InitialLoanContractOwnerPort contracts;
    private final Clock clock;private final LongSupplier nanos;
    public InitialLoanContractOrchestratorImpl(InitialLoanContractAuthority authority,InitialLoanContractLoanPort loans,InitialLoanContractOwnerPort contracts,Clock clock,LongSupplier nanos){this.authority=Objects.requireNonNull(authority);this.loans=Objects.requireNonNull(loans);this.contracts=Objects.requireNonNull(contracts);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);}
    @Override public Mono<InitialLoanContractReceipt> link(UUID app,InitialLoanContractRequest request){return Mono.defer(()->{
        Ids.require(app);Objects.requireNonNull(request);var known=new AtomicReference<InitialLoanContractReceipt>();var attempted=new AtomicBoolean();
        return initial(app,request.actionId(),InitialLoanContractAuthority.Purpose.LINK).flatMap(scope->
            owner(scope,()->loans.observe(scope)).map(basis->basis(scope,basis)).flatMap(basis->
                owner(scope,()->{attempted.set(true);return contracts.link(scope,basis,request.existingContractId()).doOnNext(value->known.set(receipt(scope,basis,request.existingContractId(),value)));})
                .flatMap(created->owner(scope,()->contracts.observe(scope)).flatMap(observed->{
                    var actual=observed.orElseThrow(()->failure(scope.actionId(),InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE));
                    if(!created.asReplay().equals(actual.asReplay()))throw failure(scope.actionId(),InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);
                    return owner(scope,()->loans.observe(scope)).map(current->{if(!basis.equals(basis(scope,current)))throw failure(scope.actionId(),InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);return created;});
                })))).onErrorMap(error->safe(request.actionId(),known.get()!=null,attempted.get(),error,false));
    });}
    @Override public Mono<Optional<InitialLoanContractReceipt>> observe(UUID app,UUID action){return Mono.defer(()->{
        Ids.require(app);Ids.require(action);var known=new AtomicReference<InitialLoanContractReceipt>();
        return initial(app,action,InitialLoanContractAuthority.Purpose.OBSERVE).flatMap(scope->
            owner(scope,()->loans.observe(scope)).map(basis->basis(scope,basis)).flatMap(basis->owner(scope,()->contracts.observe(scope).doOnNext(found->found.ifPresent(value->{requireIdentity(scope,value);known.set(value);}))).map(found->found.map(value->{
                // Current Loan ownership is required; historical Contract terms are not replaced by a current commercial projection.
                requireIdentity(scope,value);known.set(value);return value.asReplay();
            })))).onErrorMap(error->safe(action,known.get()!=null,false,error,true));
    });}
    private Mono<InitialLoanContractAuthority.Scope> initial(UUID app,UUID action,InitialLoanContractAuthority.Purpose purpose){return Mono.defer(()->{
        return authority.requireCurrent(app,action,purpose).switchIfEmpty(Mono.error(failure(action,InitialLoanContractFailure.Outcome.REFUSED)))
            .map(scope->{if(!app.equals(scope.applicationId())||!action.equals(scope.actionId())||purpose!=scope.purpose())throw failure(action,InitialLoanContractFailure.Outcome.REFUSED);
                remaining(scope);return scope;}).timeout(Duration.ofSeconds(5));
    });}
    private <T> Mono<T> owner(InitialLoanContractAuthority.Scope scope,Supplier<Mono<T>> operation){return Mono.defer(()->authority.revalidate(scope).then(Mono.defer(operation)).switchIfEmpty(Mono.error(new IllegalStateException("Actual owner response required")))
        .flatMap(value->authority.revalidate(scope).thenReturn(value)).timeout(remaining(scope)));}
    private InitialLoanContractBasis basis(InitialLoanContractAuthority.Scope scope,InitialLoanContractBasis value){if(value==null||!scope.applicationId().equals(value.applicationId())||!scope.partyId().equals(value.partyId())||!scope.loanResourceTenantId().equals(value.resourceTenantId()))throw failure(scope.actionId(),InitialLoanContractFailure.Outcome.REFUSED);return value;}
    static InitialLoanContractReceipt receipt(InitialLoanContractAuthority.Scope scope,InitialLoanContractBasis basis,UUID existing,InitialLoanContractReceipt value){requireIdentity(scope,value);if(!basis.productId().equals(value.productId())||!basis.currencyCode().equals(value.currencyCode())||existing!=null&&(!existing.equals(value.contractId())||value.contractCreated())||existing==null&&!value.contractCreated())throw failure(scope.actionId(),InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN);return value;}
    static void requireIdentity(InitialLoanContractAuthority.Scope scope,InitialLoanContractReceipt value){if(value==null||!scope.actionId().equals(value.actionId())||!scope.applicationId().equals(value.applicationId())||!scope.partyId().equals(value.partyId())||!scope.contractResourceTenantId().equals(value.resourceTenantId()))throw failure(scope.actionId(),InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE);}
    private Duration remaining(InitialLoanContractAuthority.Scope scope){long elapsed=nanos.getAsLong()-scope.observedNanos();Duration wall=Duration.between(clock.instant(),scope.notAfter()),mono=Duration.between(scope.observedAt(),scope.notAfter()).minusNanos(elapsed);if(elapsed<0||wall.isZero()||wall.isNegative()||mono.isZero()||mono.isNegative())throw new IllegalStateException("Original contract operation deadline expired");return wall.compareTo(mono)<0?wall:mono;}
    private static Throwable safe(UUID action,boolean known,boolean attempted,Throwable error,boolean read){if(known)return failure(action,InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);if(error instanceof InitialLoanContractFailure failure){if(!read&&!attempted&&failure.outcome()==InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE)return failure(action,InitialLoanContractFailure.Outcome.REFUSED);return error;}if(!attempted&&error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return new InitialLoanContractPreworkRefusal(status.getStatusCode());return failure(action,read?InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE:attempted?InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN:InitialLoanContractFailure.Outcome.REFUSED);}
    private static InitialLoanContractFailure failure(UUID action,InitialLoanContractFailure.Outcome outcome){return new InitialLoanContractFailure(action,outcome);}
}
