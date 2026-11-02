/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;

import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import static org.assertj.core.api.Assertions.*;

/** Controlled orchestration proof; owning SDK, native authorization and PostgreSQL are separate gates. */
class ManagedEvidenceDeadlineTest {
    @Test void initialAuthorityCannotHangOrDispatchAnOwner(){
        deadline(0,true,false,false,ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE,0,0);
    }
    @Test void postDocumentAuthorityTimeoutPreservesKnownCustodyAndDoesNotAttach(){
        deadline(4,false,false,false,ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE,1,0);
    }
    @Test void postAssociationAuthorityTimeoutPreservesKnownCommit(){
        deadline(10,false,false,false,ManagedEvidenceFailure.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE,1,1);
    }
    @Test void finalReceiptAuthorityCannotRestartTheOriginalBudget(){
        deadline(13,false,false,false,ManagedEvidenceFailure.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE,1,1);
    }
    @Test void dispatchedDocumentWithoutResponseRemainsUnknown(){
        deadline(0,false,true,false,ManagedEvidenceFailure.Outcome.DOCUMENT_OUTCOME_UNKNOWN,1,0);
    }
    @Test void dispatchedAssociationWithoutResponseRetainsDocumentAndUnknownAssociation(){
        deadline(0,false,false,true,ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNKNOWN,1,1);
    }
    private void deadline(int stalledCheck,boolean initial,boolean store,boolean attach,ManagedEvidenceFailure.Outcome outcome,int stores,int attachments){
        var retained=new AtomicReference<FixtureImpl>();
        StepVerifier.withVirtualTime(()->{
            var fixture=new FixtureImpl(stalledCheck,initial,store,attach);retained.set(fixture);
            return fixture.service.upload(fixture.application,fixture.request);
        }).expectSubscription().thenAwait(Duration.ofSeconds(5)).expectErrorSatisfies(error->{
            assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);
            var failure=(ManagedEvidenceFailure)error;
            assertThat(failure.outcome()).isEqualTo(outcome);
            assertThat(failure.getMetadata()).containsEntry("actionId",retained.get().action.toString())
                .containsEntry("recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY")
                .doesNotContainKeys("documentId","associationId","contentDigest","documentIntentDigest");
        }).verify(Duration.ofSeconds(2));
        assertThat(retained.get().stores).isEqualTo(stores);assertThat(retained.get().attachments).isEqualTo(attachments);
    }
    private static final class FixtureImpl {
        final UUID action=UUID.randomUUID(),application=UUID.randomUUID(),party=UUID.randomUUID(),loanTenant=UUID.randomUUID(),documentTenant=UUID.randomUUID(),document=UUID.randomUUID(),association=UUID.randomUUID(),type=UUID.randomUUID();
        final ManagedEvidenceUploadRequest request=new ManagedEvidenceUploadRequest(action,"proof.pdf","IDENTITY","application/pdf",new byte[]{1,2,3});
        final Instant epoch=Instant.parse("2026-11-01T00:00:00Z");
        final String digest="039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",intent="a".repeat(64);
        int checks,stores,attachments;
        final ManagedEvidenceOrchestratorImpl service;
        FixtureImpl(int stalledCheck,boolean initial,boolean neverStore,boolean neverAttach){
            var scheduler=VirtualTimeScheduler.get();
            Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return epoch.plusMillis(scheduler.now(TimeUnit.MILLISECONDS));}};
            var scope=new ManagedEvidenceAuthority.Scope(action,application,party,loanTenant,documentTenant,"https://issuer.invalid","opaque:human","platform",clock.instant(),scheduler.now(TimeUnit.NANOSECONDS),clock.instant().plusSeconds(5));
            var stored=new ManagedDocumentEvidencePort.Stored(action,application,party,documentTenant,document,"proof.pdf","IDENTITY","application/pdf",3,digest,intent,epoch,false);
            var attached=new ManagedLoanEvidencePort.Attached(action,application,party,loanTenant,association,documentTenant,document,type,"IDENTITY","proof.pdf","application/pdf",3,digest,intent,epoch,"ATTACHED","PENDING",epoch,false,"b".repeat(64));
            ManagedEvidenceAuthority authority=new ManagedEvidenceAuthority(){
                public Mono<Scope> requireCurrent(UUID app,UUID original){return initial?Mono.never():Mono.just(scope);}
                public Mono<Void> revalidate(Scope current){return Mono.defer(()->++checks==stalledCheck?Mono.never():Mono.empty());}
            };
            ManagedDocumentEvidencePort documents=new ManagedDocumentEvidencePort(){
                public Mono<Stored> store(ManagedEvidenceAuthority.Scope current,ManagedEvidenceUploadRequest exact){return Mono.defer(()->{stores++;return neverStore?Mono.never():Mono.just(stored);});}
                public Mono<Optional<Stored>> observe(ManagedEvidenceAuthority.Scope current){return Mono.just(Optional.of(stored));}
                public Mono<Content> readContent(ManagedEvidenceAuthority.Scope current,Stored receipt){return Mono.just(new Content(action,document,3,digest));}
            };
            ManagedLoanEvidencePort loans=new ManagedLoanEvidencePort(){
                public Mono<Optional<Attached>> observe(ManagedEvidenceAuthority.Scope current){return Mono.fromSupplier(()->attachments==0?Optional.empty():Optional.of(attached));}
                public Mono<Attached> attach(ManagedEvidenceAuthority.Scope current,ManagedDocumentEvidencePort.Stored receipt){return Mono.defer(()->{attachments++;return neverAttach?Mono.never():Mono.just(attached);});}
            };
            service=new ManagedEvidenceOrchestratorImpl(authority,documents,loans,clock,()->scheduler.now(TimeUnit.NANOSECONDS));
        }
    }
}
