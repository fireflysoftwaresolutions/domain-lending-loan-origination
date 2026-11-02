/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import static org.assertj.core.api.Assertions.*;

/** Controlled orchestration/knowledge semantics only. Genuine generated transports and owner persistence require separate gates. */
class ManagedEvidenceProgressTest {
    @Test void knownLoanReadBeforeLateAuthorityTimeoutKeepsCommittedAssociation(){
        stalled(true,ManagedEvidenceFailure.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE);
    }
    @Test void knownDocumentReadBeforeLateAuthorityTimeoutKeepsStoredCustody(){
        stalled(false,ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE);
    }
    @Test void genuineReadOnlyAuthorizationRefusalDoesNotBecomeUnknownOrDispatchAWrite(){
        var fixture=new FixtureImpl("read-forbidden",false);
        StepVerifier.create(fixture.service.upload(fixture.application,fixture.request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ResponseStatusException.class);assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(403);}).verify();fixture.noWrites();
    }
    @Test void response503AfterDocumentDispatchKeepsOriginalActionUnknown(){
        var fixture=new FixtureImpl("write-unavailable",false);
        StepVerifier.create(fixture.service.upload(fixture.application,fixture.request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);assertThat(((ManagedEvidenceFailure)error).outcome()).isEqualTo(ManagedEvidenceFailure.Outcome.DOCUMENT_OUTCOME_UNKNOWN);assertThat(((ManagedEvidenceFailure)error).getMetadata()).containsEntry("actionId",fixture.action.toString()).doesNotContainKeys("documentId","associationId");}).verify();assertThat(fixture.stores).isEqualTo(1);assertThat(fixture.attachments).isZero();
    }
    @Test void invalidForeignOwnerReceiptCannotClaimKnownAssociation(){
        var fixture=new FixtureImpl("foreign-association",false);
        StepVerifier.create(fixture.service.upload(fixture.application,fixture.request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);assertThat(((ManagedEvidenceFailure)error).outcome()).isEqualTo(ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE);assertThat(((ManagedEvidenceFailure)error).getMetadata()).doesNotContainKeys("documentId","associationId");}).verify();fixture.noWrites();
    }
    @Test void transportProvenNoDispatchDoesNotBecomeUnknown(){outcome("write-predispatch",ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE,false);}
    @Test void typedOwnerKnownCommitWithoutReceiptCannotBecomeUnknown(){outcome("write-known-no-receipt",ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE,false);}
    @Test void ownerReceiptObservedBeforeTransportPostcheckKeepsKnownCustody(){outcome("write-known-receipt",ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE,true);}
    @Test void filenameOver200IsRejectedBeforeAuthorityOrEitherOwner(){
        var fixture=new FixtureImpl("normal",false);var request=new ManagedEvidenceUploadRequest(fixture.action,"a".repeat(201),"ID_DOCUMENT","application/pdf",new byte[]{1,2,3});
        StepVerifier.create(fixture.service.upload(fixture.application,request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(org.fireflyframework.web.error.exceptions.BusinessException.class);assertThat(((org.fireflyframework.web.error.exceptions.BusinessException)error).getCode()).isEqualTo("MANAGED_EVIDENCE_INPUT_INVALID");}).verify();assertThat(fixture.admissions).isZero();fixture.noWrites();
    }
    @Test void initialPolicyProviderFailureIsTypedUnavailableBeforeAnyOwner(){
        var fixture=new FixtureImpl("initial-pdp-unavailable",false);StepVerifier.create(fixture.service.upload(fixture.application,fixture.request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);var failure=(ManagedEvidenceFailure)error;assertThat(failure.outcome()).isEqualTo(ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE);assertThat(failure.getMetadata()).containsEntry("actionId",fixture.action.toString()).doesNotContainKeys("documentId","associationId");}).verify();fixture.noWrites();
    }
    @Test void provenAssociationNoDispatchPreservesDocumentWithoutInventingUnknown(){associationOutcome("association-predispatch",ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE);}
    @Test void associationKnownCommitWithoutReceiptCannotBeDowngradedToUnknown(){associationOutcome("association-known-no-receipt",ManagedEvidenceFailure.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE);}
    @Test void knownAssociationReceiptSurvivesTransportPostcheck(){associationOutcome("association-known-receipt",ManagedEvidenceFailure.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE);}
    void associationOutcome(String mode,ManagedEvidenceFailure.Outcome outcome){
        var fixture=new FixtureImpl(mode,false);StepVerifier.create(fixture.service.upload(fixture.application,fixture.request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);var failure=(ManagedEvidenceFailure)error;assertThat(failure.outcome()).isEqualTo(outcome);assertThat(failure.getMetadata()).containsEntry("actionId",fixture.action.toString()).containsEntry("documentId",fixture.document.toString());if("association-known-receipt".equals(mode))assertThat(failure.getMetadata()).containsEntry("associationId",fixture.association.toString());else assertThat(failure.getMetadata()).doesNotContainKey("associationId");}).verify();assertThat(fixture.stores).isEqualTo(1);assertThat(fixture.attachments).isEqualTo(1);
    }
    void outcome(String mode,ManagedEvidenceFailure.Outcome outcome,boolean known){
        var fixture=new FixtureImpl(mode,false);StepVerifier.create(fixture.service.upload(fixture.application,fixture.request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);var failure=(ManagedEvidenceFailure)error;assertThat(failure.outcome()).isEqualTo(outcome);assertThat(failure.getMetadata()).containsEntry("actionId",fixture.action.toString());if(known)assertThat(failure.getMetadata()).containsEntry("documentId",fixture.document.toString());else assertThat(failure.getMetadata()).doesNotContainKey("documentId");assertThat(failure.getMetadata()).doesNotContainKey("associationId");}).verify();assertThat(fixture.stores).isEqualTo(1);assertThat(fixture.attachments).isZero();
    }
    void stalled(boolean loan,ManagedEvidenceFailure.Outcome outcome){
        var held=new AtomicReference<FixtureImpl>();
        StepVerifier.withVirtualTime(()->{var fixture=new FixtureImpl(loan?"known-loan":"known-document",true);held.set(fixture);return loan?fixture.service.upload(fixture.application,fixture.request):fixture.service.recover(fixture.application,fixture.action);})
            .thenAwait(Duration.ofSeconds(5)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);assertThat(((ManagedEvidenceFailure)error).outcome()).isEqualTo(outcome);assertThat(((ManagedEvidenceFailure)error).getMetadata()).containsEntry("actionId",held.get().action.toString()).doesNotContainKeys("documentId","associationId");}).verify(Duration.ofSeconds(2));held.get().noWrites();
    }
    static final class FixtureImpl {
        final UUID action=UUID.randomUUID(),application=UUID.randomUUID(),party=UUID.randomUUID(),loanTenant=UUID.randomUUID(),documentTenant=UUID.randomUUID(),document=UUID.randomUUID(),association=UUID.randomUUID(),type=UUID.randomUUID();
        final ManagedEvidenceUploadRequest request=new ManagedEvidenceUploadRequest(action,"proof.pdf","ID_DOCUMENT","application/pdf",new byte[]{1,2,3});
        final Instant epoch=Instant.parse("2026-11-01T00:00:00Z");final String digest="039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",intent="a".repeat(64);
        int checks,stores,attachments,admissions;final ManagedEvidenceOrchestratorImpl service;
        FixtureImpl(String mode,boolean virtual){
            var scheduler=virtual?VirtualTimeScheduler.get():null;Clock clock=virtual?new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return epoch.plusMillis(scheduler.now(TimeUnit.MILLISECONDS));}}:Clock.systemUTC();
            java.util.function.LongSupplier nanos=virtual?()->scheduler.now(TimeUnit.NANOSECONDS):System::nanoTime;
            Instant observed=clock.instant();long observedNanos=nanos.getAsLong();
            var scope=new ManagedEvidenceAuthority.Scope(action,application,party,loanTenant,documentTenant,"https://issuer.invalid","opaque:human","platform",observed,observedNanos,observed.plusSeconds(5));
            var stored=new ManagedDocumentEvidencePort.Stored(action,application,party,documentTenant,document,"proof.pdf","ID_DOCUMENT","application/pdf",3,digest,intent,epoch,true);
            var attached=new ManagedLoanEvidencePort.Attached(action,"foreign-association".equals(mode)?UUID.randomUUID():application,party,loanTenant,association,documentTenant,document,type,"ID_DOCUMENT","proof.pdf","application/pdf",3,digest,intent,epoch,"ATTACHED","PENDING",epoch,true,"b".repeat(64));
            ManagedEvidenceAuthority authority=new ManagedEvidenceAuthority(){public Mono<Scope> requireCurrent(UUID app,UUID original){admissions++;return "initial-pdp-unavailable".equals(mode)?Mono.error(new IllegalStateException("Controlled policy provider unavailable")):Mono.just(scope);}public Mono<Void> revalidate(Scope current){return Mono.defer(()->virtual&&++checks==2?Mono.never():Mono.empty());}};
            ManagedDocumentEvidencePort documents=new ManagedDocumentEvidencePort(){
                public Mono<Stored> store(ManagedEvidenceAuthority.Scope current,ManagedEvidenceUploadRequest exact){return Mono.defer(()->{stores++;
                    if("write-predispatch".equals(mode))return Mono.error(new OutcomeFailure(current,CustodyOutcome.PROVEN_REFUSED,null));
                    if("write-known-no-receipt".equals(mode))return Mono.error(new OutcomeFailure(current,CustodyOutcome.COMMITTED,null));
                    if("write-known-receipt".equals(mode))return Mono.error(new OutcomeFailure(current,CustodyOutcome.COMMITTED,stored));
                    return "write-unavailable".equals(mode)?Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)):Mono.just(stored);});}
                public Mono<Optional<Stored>> observe(ManagedEvidenceAuthority.Scope current){return Mono.just(Optional.of(stored));}
                public Mono<Content> readContent(ManagedEvidenceAuthority.Scope current,Stored receipt){return Mono.just(new Content(action,document,3,digest));}
            };
            ManagedLoanEvidencePort loans=new ManagedLoanEvidencePort(){
                public Mono<Optional<Attached>> observe(ManagedEvidenceAuthority.Scope current){return "read-forbidden".equals(mode)?Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)):Mono.just(Set.of("known-loan","foreign-association").contains(mode)?Optional.of(attached):Optional.empty());}
                public Mono<Attached> attach(ManagedEvidenceAuthority.Scope current,ManagedDocumentEvidencePort.Stored receipt){attachments++;if("association-predispatch".equals(mode))return Mono.error(new OutcomeFailure(current,AssociationOutcome.PROVEN_REFUSED,null));if("association-known-no-receipt".equals(mode))return Mono.error(new OutcomeFailure(current,AssociationOutcome.COMMITTED,null));if("association-known-receipt".equals(mode))return Mono.error(new OutcomeFailure(current,AssociationOutcome.COMMITTED,attached));return Mono.just(attached);}
            };
            service=new ManagedEvidenceOrchestratorImpl(authority,documents,loans,clock,nanos);
        }
        void noWrites(){assertThat(stores).isZero();assertThat(attachments).isZero();}
    }
}
