/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled orchestration fixtures; owning PostgreSQL/Main acceptance is separate. */
class InitialLoanContractOrchestratorTest {
    static final Instant NOW=Instant.parse("2026-11-02T00:00:00Z");static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    static final UUID APP=id(1),ACTION=id(2),PARTY=id(3),LOAN=id(4),CONTRACT_TENANT=id(5),ROLE=id(6),CONTRACT=id(7),CONTRACT_PARTY=id(8),PRODUCT=id(9),PRIMARY=id(10),SIMULATION=id(11);
    static UUID id(long value){return new UUID(0,value);}
    static InitialLoanContractBasis basis(){return new InitialLoanContractBasis(APP,LOAN,PARTY,PRIMARY,SIMULATION,PRODUCT,new BigDecimal("1000.00"),12,"EUR","a".repeat(64));}
    static InitialLoanContractReceipt receipt(){return new InitialLoanContractReceipt(ACTION,APP,CONTRACT_TENANT,PARTY,ROLE,CONTRACT,CONTRACT_PARTY,PRODUCT,"EUR","INITIAL_PRIMARY_LOAN_CONTRACT","DRAFT",true,NOW,"b".repeat(64),false);}
    static final class Fixture {
        final AtomicInteger writes=new AtomicInteger(),reads=new AtomicInteger(),loanReads=new AtomicInteger(),checks=new AtomicInteger();
        Supplier<Mono<InitialLoanContractAuthority.Scope>> acquire;Supplier<Mono<Void>> check=Mono::empty;
        Supplier<Mono<InitialLoanContractBasis>> loan=()->Mono.just(basis());Supplier<Mono<InitialLoanContractReceipt>> write=()->Mono.just(receipt());Supplier<Mono<Optional<InitialLoanContractReceipt>>> read=()->Mono.just(Optional.of(receipt().asReplay()));
        Fixture(){acquire=()->Mono.just(new InitialLoanContractAuthority.Scope(ACTION,APP,PARTY,LOAN,CONTRACT_TENANT,"https://issuer.example","original-human","opaque-tenant",InitialLoanContractAuthority.Purpose.LINK,NOW,System.nanoTime(),NOW.plusSeconds(5)));}
        InitialLoanContractService service(){return new InitialLoanContractOrchestratorImpl(new InitialLoanContractAuthority(){
            public <T> Mono<T> invoke(UUID app,UUID action,Purpose purpose,Supplier<Mono<T>> operation){return Mono.defer(operation);}
            public Mono<Scope> requireCurrent(UUID app,UUID action,Purpose purpose){return acquire.get().map(s->new Scope(s.actionId(),s.applicationId(),s.partyId(),s.loanResourceTenantId(),s.contractResourceTenantId(),s.issuer(),s.subject(),s.platformTenant(),purpose,s.observedAt(),s.observedNanos(),s.notAfter()));}
            public Mono<Void> revalidate(Scope s){checks.incrementAndGet();return check.get();}public Mono<Void> requireTransportContext(Scope s){return Mono.empty();}
        },scope->{loanReads.incrementAndGet();return loan.get();},new InitialLoanContractOwnerPort(){
            public Mono<InitialLoanContractReceipt> link(InitialLoanContractAuthority.Scope s,InitialLoanContractBasis b,UUID existing){assertEquals(APP,s.applicationId());assertEquals(ACTION,s.actionId());assertEquals(basis(),b);writes.incrementAndGet();return write.get();}
            public Mono<Optional<InitialLoanContractReceipt>> observe(InitialLoanContractAuthority.Scope s){reads.incrementAndGet();return read.get();}
        },CLOCK,System::nanoTime);}
        Mono<InitialLoanContractReceipt> execute(){return service().link(APP,new InitialLoanContractRequest(ACTION,null));}
    }
    @Test void actualOwnerReceiptRequiresExactOriginalIdentityAndPersistedReadback(){var f=new Fixture();StepVerifier.create(f.execute()).expectNext(receipt()).verifyComplete();assertEquals(1,f.writes.get());assertEquals(1,f.reads.get());assertEquals(2,f.loanReads.get());}
    @Test void wrongCurrentLoanPartyRefusesBeforeAnyContractWrite(){var f=new Fixture();f.loan=()->Mono.just(new InitialLoanContractBasis(APP,LOAN,id(20),PRIMARY,SIMULATION,PRODUCT,new BigDecimal("1000"),12,"EUR","a".repeat(64)));StepVerifier.create(f.execute()).expectErrorMatches(InitialLoanContractOrchestratorTest::refused).verify();assertEquals(0,f.writes.get());}
    @Test void absentCurrentApplicationCannotBecomeAContractOrInventedApplication(){var f=new Fixture();f.loan=Mono::empty;StepVerifier.create(f.execute()).expectError(InitialLoanContractFailure.class).verify();assertEquals(0,f.writes.get());}
    @Test void initialAuthorityNeverIsBoundedAndSendsNoMutation(){var f=new Fixture();f.acquire=Mono::never;StepVerifier.withVirtualTime(f::execute).thenAwait(Duration.ofSeconds(5)).expectErrorMatches(InitialLoanContractOrchestratorTest::refused).verify();assertEquals(0,f.writes.get());}
    @Test void prewriteRevalidationNeverIsBoundedWithoutMutation(){var f=new Fixture();f.check=Mono::never;StepVerifier.withVirtualTime(f::execute).thenAwait(Duration.ofSeconds(5)).expectErrorMatches(InitialLoanContractOrchestratorTest::refused).verify();assertEquals(0,f.writes.get());}
    @Test void emptyOwnerAfterInvocationIsUnknownWithoutRetry(){var f=new Fixture();f.write=Mono::empty;StepVerifier.create(f.execute()).expectErrorMatches(e->e instanceof InitialLoanContractFailure v&&v.outcome()==InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN).verify();assertEquals(1,f.writes.get());assertEquals(0,f.reads.get());}
    @Test void outerOwnerTimeoutAfterLinkInvocationIsUnknownWithoutAReceipt(){var f=new Fixture();var cancelled=new AtomicBoolean();f.write=()->Mono.<InitialLoanContractReceipt>never().doOnCancel(()->cancelled.set(true));StepVerifier.withVirtualTime(f::execute).thenAwait(Duration.ofSeconds(5)).expectErrorMatches(e->e instanceof InitialLoanContractFailure v&&v.outcome()==InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN&&ACTION.equals(v.actionId())).verify();assertEquals(1,f.writes.get());assertTrue(cancelled.get());assertEquals(0,f.reads.get());}
    @Test void postCommitRevalidationNeverPreservesKnownCommitAndOriginalAction(){var f=new Fixture();f.write=()->{f.check=Mono::never;return Mono.just(receipt());};StepVerifier.withVirtualTime(f::execute).thenAwait(Duration.ofSeconds(5)).expectErrorMatches(InitialLoanContractOrchestratorTest::committed).verify();assertEquals(1,f.writes.get());assertEquals(0,f.reads.get());}
    @Test void postCommitAuthorityRefusalCannotBecomeAPrework403(){var f=new Fixture();f.write=()->{f.check=()->Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));return Mono.just(receipt());};StepVerifier.create(f.execute()).expectErrorMatches(InitialLoanContractOrchestratorTest::committed).verify();assertEquals(1,f.writes.get());}
    @Test void failedOwnerReadAfterCreatePreservesKnownCommitWithoutAnotherWrite(){var f=new Fixture();f.read=()->Mono.error(new InitialLoanContractFailure(ACTION,InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE));StepVerifier.create(f.execute()).expectErrorMatches(InitialLoanContractOrchestratorTest::committed).verify();assertEquals(1,f.writes.get());}
    @Test void unknownDispatchedOwnerOutcomeRetainsOriginalActionAndNeverRetries(){var f=new Fixture();f.write=()->Mono.error(new InitialLoanContractFailure(ACTION,InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN));StepVerifier.create(f.execute()).expectErrorMatches(e->e instanceof InitialLoanContractFailure v&&v.outcome()==InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN&&ACTION.equals(v.actionId())).verify();assertEquals(1,f.writes.get());assertEquals(0,f.reads.get());}
    @Test void changedReadbackCannotBeAcceptedOrDeleteKnownContract(){var f=new Fixture();f.read=()->Mono.just(Optional.of(new InitialLoanContractReceipt(ACTION,APP,CONTRACT_TENANT,PARTY,ROLE,id(30),CONTRACT_PARTY,PRODUCT,"EUR","INITIAL_PRIMARY_LOAN_CONTRACT","DRAFT",true,NOW,"b".repeat(64),true)));StepVerifier.create(f.execute()).expectErrorMatches(InitialLoanContractOrchestratorTest::committed).verify();assertEquals(1,f.writes.get());}
    @Test void getMissingOriginalActionIsReadOnly(){var f=new Fixture();f.read=()->Mono.just(Optional.empty());StepVerifier.create(f.service().observe(APP,ACTION)).expectNext(Optional.empty()).verifyComplete();assertEquals(0,f.writes.get());assertEquals(1,f.reads.get());}
    @Test void historicalDraftReceiptRemainsReadableAfterLegitimateContractLifecycle(){var f=new Fixture();StepVerifier.create(f.service().observe(APP,ACTION)).expectNext(Optional.of(receipt().asReplay())).verifyComplete();assertEquals(0,f.writes.get());}
    @Test void lateObserveRevalidationPreservesKnownReceiptOutcome(){var f=new Fixture();f.read=()->{f.check=()->Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));return Mono.just(Optional.of(receipt().asReplay()));};StepVerifier.create(f.service().observe(APP,ACTION)).expectErrorMatches(InitialLoanContractOrchestratorTest::committed).verify();assertEquals(0,f.writes.get());}
    static boolean refused(Throwable e){return e instanceof InitialLoanContractFailure v&&v.outcome()==InitialLoanContractFailure.Outcome.REFUSED&&ACTION.equals(v.actionId());}
    static boolean committed(Throwable e){return e instanceof InitialLoanContractFailure v&&v.outcome()==InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE&&ACTION.equals(v.actionId());}
}
