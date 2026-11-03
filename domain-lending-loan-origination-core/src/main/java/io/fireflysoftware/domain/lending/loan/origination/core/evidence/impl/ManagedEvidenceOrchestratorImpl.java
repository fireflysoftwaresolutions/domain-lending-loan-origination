/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;

import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/** No database or automatic compensation: each owner retains its committed effects and original action. */
public final class ManagedEvidenceOrchestratorImpl implements ManagedEvidenceService {
    private final ManagedEvidenceAuthority authority;
    private final ManagedDocumentEvidencePort documents;
    private final ManagedLoanEvidencePort loans;
    private final Clock clock;
    private final LongSupplier nanos;
    public ManagedEvidenceOrchestratorImpl(ManagedEvidenceAuthority authority,ManagedDocumentEvidencePort documents,
            ManagedLoanEvidencePort loans,Clock clock,LongSupplier nanos){
        this.authority=Objects.requireNonNull(authority);this.documents=Objects.requireNonNull(documents);this.loans=Objects.requireNonNull(loans);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
    }
    @Override public Mono<ManagedEvidenceReceipt> upload(UUID applicationId,ManagedEvidenceUploadRequest raw){
        final ManagedEvidenceUploadRequest request;
        try{request=capture(applicationId,raw);}catch(RuntimeException invalid){return Mono.error(invalid());}
        return admitted(applicationId,request.actionId(),(scope,deadline)->{
            checked(scope,applicationId,request.actionId(),deadline);var progress=new ProgressImpl();
            return observeAssociation(scope,progress,deadline).flatMap(existing->{
                if(existing.isPresent())return observeDocument(scope,progress,deadline).flatMap(stored->{requireRequest(request,stored);return finish(scope,progress,stored,existing.orElseThrow(),true,deadline);});
                return owner(scope,()->{progress.documentDispatched=true;return documents.store(scope,request).doOnNext(stored->{verifyDocument(scope,stored);requireRequest(request,stored);progress.document=stored;progress.documentCommitted=true;}).doOnError(ManagedDocumentEvidencePort.OutcomeFailure.class,error->custodyOutcome(scope,progress,error));},deadline)
                    .flatMap(stored->observeDocument(scope,progress,deadline).flatMap(read->{sameDocument(stored,read);return associate(scope,progress,read,deadline);}));
            }).onErrorResume(error->failure(scope,progress,error,deadline));
        });
    }
    @Override public Mono<ManagedEvidenceReceipt> recover(UUID applicationId,UUID originalActionId){
        if(!validId(applicationId)||!validId(originalActionId))return Mono.error(invalid());
        return admitted(applicationId,originalActionId,(scope,deadline)->{
            checked(scope,applicationId,originalActionId,deadline);var progress=new ProgressImpl();
            return observeDocument(scope,progress,deadline).flatMap(stored->observeAssociation(scope,progress,deadline).flatMap(existing->{
                if(existing.isPresent())return finish(scope,progress,stored,existing.orElseThrow(),true,deadline);
                return associate(scope,progress,stored,deadline);
            })).onErrorResume(error->failure(scope,progress,error,deadline));
        });
    }
    private Mono<ManagedEvidenceReceipt> associate(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,
            ManagedDocumentEvidencePort.Stored stored,DeadlineImpl deadline){
        if(!progress.contentVerified)return Mono.error(unavailable());
        return owner(scope,()->{progress.associationDispatched=true;return loans.attach(scope,stored).doOnNext(attached->{verifyAssociation(scope,stored,attached);progress.association=attached;progress.associationCommitted=true;}).doOnError(ManagedLoanEvidencePort.OutcomeFailure.class,error->associationOutcome(scope,progress,error));},deadline)
            .flatMap(attached->observeAssociation(scope,progress,deadline).flatMap(read->{
                if(read.isEmpty())return Mono.error(unavailable());sameAssociation(attached,read.orElseThrow());return finish(scope,progress,stored,read.orElseThrow(),attached.replayed(),deadline);
            }));
    }
    private Mono<ManagedDocumentEvidencePort.Stored> observeDocument(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,DeadlineImpl deadline){
        return observed(scope,()->documents.observe(scope).doOnNext(found->found.ifPresent(stored->{verifyDocument(scope,stored);progress.document=stored;progress.documentCommitted=true;})).doOnError(ManagedDocumentEvidencePort.OutcomeFailure.class,error->custodyOutcome(scope,progress,error)),deadline).flatMap(found->{
            if(found.isEmpty())return Mono.error(unavailable());var stored=found.orElseThrow();verifyDocument(scope,stored);progress.document=stored;
            return observed(scope,()->documents.readContent(scope,stored),deadline).map(content->{
                if(!stored.actionId().equals(content.actionId())||!stored.documentId().equals(content.documentId())
                        ||stored.contentLength()!=content.contentLength()||!stored.contentDigest().equals(content.contentDigest()))throw unavailable();
                progress.contentVerified=true;return stored;
            });
        });
    }
    private Mono<Optional<ManagedLoanEvidencePort.Attached>> observeAssociation(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,DeadlineImpl deadline){
        return observed(scope,()->loans.observe(scope).doOnNext(found->found.ifPresent(attached->{verifyObservedAssociation(scope,attached);progress.association=attached;progress.associationCommitted=true;})).doOnError(ManagedLoanEvidencePort.OutcomeFailure.class,error->associationOutcome(scope,progress,error)),deadline);
    }
    private Mono<ManagedEvidenceReceipt> finish(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,
            ManagedDocumentEvidencePort.Stored stored,ManagedLoanEvidencePort.Attached attached,boolean replayed,DeadlineImpl deadline){
        verifyAssociation(scope,stored,attached);if(!progress.contentVerified)return Mono.error(unavailable());progress.association=attached;
        return authority.revalidate(scope).then(Mono.fromCallable(()->{remaining(scope,deadline);return new ManagedEvidenceReceipt(scope.actionId(),scope.applicationId(),scope.partyId(),stored.documentId(),attached.associationId(),stored.fileName(),stored.documentTypeCode(),stored.mediaType(),stored.contentLength(),stored.contentDigest(),stored.ownerIntentDigest(),attached.ownerIntentDigest(),stored.storedAt(),attached.attachedAt(),attached.state(),attached.verificationStatus(),replayed);})).timeout(remaining(scope,deadline));
    }
    private <T> Mono<T> observed(ManagedEvidenceAuthority.Scope scope,Supplier<Mono<T>> read,DeadlineImpl deadline){return owner(scope,read,deadline);}
    private <T> Mono<T> owner(ManagedEvidenceAuthority.Scope scope,Supplier<Mono<T>> operation,DeadlineImpl deadline){return Mono.defer(()->
        authority.revalidate(scope).then(Mono.defer(operation)).switchIfEmpty(Mono.error(unavailable()))
            .flatMap(value->authority.revalidate(scope).then(Mono.fromCallable(()->{remaining(scope,deadline);return value;})))
            .timeout(remaining(scope,deadline)));}
    private <T> Mono<T> failure(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,Throwable error,DeadlineImpl deadline){
        return Mono.defer(()->authority.revalidate(scope).timeout(remaining(scope,deadline))).thenReturn(true).onErrorReturn(false).flatMap(current->{
            if(current&&error instanceof ManagedEvidenceConflict&&!progress.associationDispatched)return Mono.error(error);
            if(current&&!progress.documentDispatched&&!progress.associationDispatched&&progress.document==null&&progress.association==null
                    &&error instanceof org.springframework.web.server.ResponseStatusException refusal&&Set.of(400,401,403).contains(refusal.getStatusCode().value()))return Mono.error(error);
            var outcome=progress.association!=null||progress.associationCommitted?ManagedEvidenceFailure.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE:
                (progress.document!=null||progress.documentCommitted)?(progress.associationDispatched&&!progress.associationRefused?ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNKNOWN:ManagedEvidenceFailure.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE):
                progress.documentDispatched&&!progress.documentRefused?ManagedEvidenceFailure.Outcome.DOCUMENT_OUTCOME_UNKNOWN:ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE;
            var details=new LinkedHashMap<String,Object>();
            if(current&&progress.document!=null){details.put("documentId",progress.document.documentId().toString());if(progress.contentVerified){details.put("contentDigest",progress.document.contentDigest());details.put("documentIntentDigest",progress.document.ownerIntentDigest());}}
            if(current&&progress.association!=null)details.put("associationId",progress.association.associationId().toString());
            return Mono.error(new ManagedEvidenceFailure(scope.actionId(),outcome,details));
        });
    }
    private void associationOutcome(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,ManagedLoanEvidencePort.OutcomeFailure error){
        if(!error.matches(scope))throw unavailable();
        if(error.outcome()==ManagedLoanEvidencePort.AssociationOutcome.COMMITTED){error.known().ifPresent(attached->{verifyObservedAssociation(scope,attached);progress.association=attached;});progress.associationCommitted=true;}
        else if(error.outcome()==ManagedLoanEvidencePort.AssociationOutcome.PROVEN_REFUSED)progress.associationRefused=true;
    }
    private void custodyOutcome(ManagedEvidenceAuthority.Scope scope,ProgressImpl progress,ManagedDocumentEvidencePort.OutcomeFailure error){
        if(!error.matches(scope))throw unavailable();
        if(error.outcome()==ManagedDocumentEvidencePort.CustodyOutcome.COMMITTED){error.known().ifPresent(stored->{verifyDocument(scope,stored);progress.document=stored;});progress.documentCommitted=true;}
        else if(error.outcome()==ManagedDocumentEvidencePort.CustodyOutcome.PROVEN_REFUSED)progress.documentRefused=true;
    }
    private void checked(ManagedEvidenceAuthority.Scope scope,UUID application,UUID action,DeadlineImpl deadline){if(!application.equals(scope.applicationId())||!action.equals(scope.actionId()))throw unavailable();remaining(scope,deadline);}
    private <T> Mono<T> admitted(UUID application,UUID action,BiFunction<ManagedEvidenceAuthority.Scope,DeadlineImpl,Mono<T>> operation){
        return Mono.defer(()->{
            var deadline=new DeadlineImpl(clock.instant(),nanos.getAsLong());
            return authority.requireCurrent(application,action).switchIfEmpty(Mono.error(unavailable())).timeout(remaining(deadline))
                .onErrorMap(error->beforeDispatchFailure(action,error)).flatMap(scope->Mono.defer(()->operation.apply(scope,deadline)));
        }).onErrorMap(java.util.concurrent.TimeoutException.class,error->new ManagedEvidenceFailure(action,ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE,Map.of()));
    }
    private static Throwable beforeDispatchFailure(UUID action,Throwable error){
        if(error instanceof org.springframework.web.server.ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return error;
        return new ManagedEvidenceFailure(action,ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE,Map.of());
    }
    private Duration remaining(DeadlineImpl deadline){
        long elapsed=nanos.getAsLong()-deadline.observedNanos();Duration wall=Duration.between(clock.instant(),deadline.observedAt().plusSeconds(5)),monotonic=Duration.ofSeconds(5).minusNanos(elapsed);
        if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;
    }
    private Duration remaining(ManagedEvidenceAuthority.Scope scope,DeadlineImpl deadline){
        long elapsed=nanos.getAsLong()-scope.observedNanos();var wall=Duration.between(clock.instant(),scope.notAfter());var monotonic=Duration.between(scope.observedAt(),scope.notAfter()).minusNanos(elapsed);
        if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();
        Duration scoped=wall.compareTo(monotonic)<0?wall:monotonic,original=remaining(deadline);return scoped.compareTo(original)<0?scoped:original;
    }
    private static void verifyDocument(ManagedEvidenceAuthority.Scope scope,ManagedDocumentEvidencePort.Stored stored){
        if(stored==null||!scope.actionId().equals(stored.actionId())||!scope.applicationId().equals(stored.applicationId())||!scope.partyId().equals(stored.partyId())
                ||!scope.documentResourceTenantId().equals(stored.resourceTenantId())||!validId(stored.documentId())||stored.storedAt()==null
                ||stored.contentLength()<1||stored.contentLength()>2097152||!digest(stored.contentDigest())||!digest(stored.ownerIntentDigest())
                ||stored.fileName()==null||stored.mediaType()==null||stored.documentTypeCode()==null)throw unavailable();
    }
    private static void verifyObservedAssociation(ManagedEvidenceAuthority.Scope scope,ManagedLoanEvidencePort.Attached attached){
        if(attached==null||!scope.actionId().equals(attached.actionId())||!scope.applicationId().equals(attached.applicationId())||!scope.partyId().equals(attached.partyId())
                ||!scope.loanResourceTenantId().equals(attached.resourceTenantId())||!scope.documentResourceTenantId().equals(attached.documentResourceTenantId())||!validId(attached.associationId())||!validId(attached.documentId())||!validId(attached.documentTypeId())
                ||attached.documentTypeCode()==null||!attached.documentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")||attached.fileName()==null||attached.fileName().isBlank()||attached.fileName().length()>200
                ||attached.mediaType()==null||!attached.mediaType().matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")||attached.contentLength()<1||attached.contentLength()>2097152
                ||!digest(attached.contentDigest())||!digest(attached.documentIntentDigest())||!digest(attached.ownerIntentDigest())||attached.documentStoredAt()==null||!"ATTACHED".equals(attached.state())||!"PENDING".equals(attached.verificationStatus())||attached.attachedAt()==null)throw unavailable();
    }
    private static void verifyAssociation(ManagedEvidenceAuthority.Scope scope,ManagedDocumentEvidencePort.Stored stored,ManagedLoanEvidencePort.Attached attached){
        if(attached==null||!scope.actionId().equals(attached.actionId())||!scope.applicationId().equals(attached.applicationId())||!scope.partyId().equals(attached.partyId())
                ||!scope.loanResourceTenantId().equals(attached.resourceTenantId())||!scope.documentResourceTenantId().equals(attached.documentResourceTenantId())||!validId(attached.associationId())||!validId(attached.documentTypeId())
                ||!stored.documentId().equals(attached.documentId())||!stored.documentTypeCode().equals(attached.documentTypeCode())||!stored.fileName().equals(attached.fileName())
                ||!stored.mediaType().equals(attached.mediaType())||stored.contentLength()!=attached.contentLength()||!stored.contentDigest().equals(attached.contentDigest())
                ||!stored.ownerIntentDigest().equals(attached.documentIntentDigest())||!stored.storedAt().equals(attached.documentStoredAt())||!digest(attached.ownerIntentDigest())||!"ATTACHED".equals(attached.state())||!"PENDING".equals(attached.verificationStatus())||attached.attachedAt()==null)throw unavailable();
    }
    private static void requireRequest(ManagedEvidenceUploadRequest request,ManagedDocumentEvidencePort.Stored stored){
        if(!request.actionId().equals(stored.actionId())||!request.fileName().equals(stored.fileName())||!request.documentTypeCode().equals(stored.documentTypeCode())
                ||!request.mediaType().equals(stored.mediaType())||request.content().length!=stored.contentLength()||!hash(request.content()).equals(stored.contentDigest()))throw new ManagedEvidenceConflict(request.actionId());
    }
    private static void sameDocument(ManagedDocumentEvidencePort.Stored first,ManagedDocumentEvidencePort.Stored second){
        if(!first.actionId().equals(second.actionId())||!first.applicationId().equals(second.applicationId())||!first.partyId().equals(second.partyId())||!first.resourceTenantId().equals(second.resourceTenantId())
                ||!first.documentId().equals(second.documentId())||!first.fileName().equals(second.fileName())||!first.documentTypeCode().equals(second.documentTypeCode())||!first.mediaType().equals(second.mediaType())
                ||first.contentLength()!=second.contentLength()||!first.contentDigest().equals(second.contentDigest())||!first.ownerIntentDigest().equals(second.ownerIntentDigest())||!first.storedAt().equals(second.storedAt()))throw unavailable();
    }
    private static void sameAssociation(ManagedLoanEvidencePort.Attached first,ManagedLoanEvidencePort.Attached second){
        if(!new ManagedLoanEvidencePort.Attached(first.actionId(),first.applicationId(),first.partyId(),first.resourceTenantId(),first.associationId(),first.documentResourceTenantId(),first.documentId(),first.documentTypeId(),first.documentTypeCode(),first.fileName(),first.mediaType(),first.contentLength(),first.contentDigest(),first.documentIntentDigest(),first.documentStoredAt(),first.state(),first.verificationStatus(),first.attachedAt(),true,first.ownerIntentDigest()).equals(
                new ManagedLoanEvidencePort.Attached(second.actionId(),second.applicationId(),second.partyId(),second.resourceTenantId(),second.associationId(),second.documentResourceTenantId(),second.documentId(),second.documentTypeId(),second.documentTypeCode(),second.fileName(),second.mediaType(),second.contentLength(),second.contentDigest(),second.documentIntentDigest(),second.documentStoredAt(),second.state(),second.verificationStatus(),second.attachedAt(),true,second.ownerIntentDigest())))throw unavailable();
    }
    private static ManagedEvidenceUploadRequest capture(UUID application,ManagedEvidenceUploadRequest request){
        if(!validId(application)||request==null||!validId(request.actionId()))throw invalid();byte[] bytes=request.content();
        if(bytes==null||bytes.length<1||bytes.length>2097152)throw invalid();
        for(String text:new String[]{request.fileName(),request.documentTypeCode(),request.mediaType()}){
            if(text==null||text.isBlank()||!text.equals(text.strip())||text.codePoints().anyMatch(Character::isISOControl))throw invalid();
            try{StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));}catch(CharacterCodingException invalid){throw invalid();}
        }
        if(request.fileName().length()>200||request.fileName().contains("/")||request.fileName().contains("\\")||Set.of(".","..").contains(request.fileName())
                ||!request.documentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")||request.mediaType().length()>100||!request.mediaType().matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))throw invalid();
        return new ManagedEvidenceUploadRequest(request.actionId(),request.fileName(),request.documentTypeCode(),request.mediaType(),bytes);
    }
    private static String hash(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException impossible){throw unavailable();}}
    private static boolean validId(UUID id){return id!=null&&!id.equals(new UUID(0,0));}
    private static boolean digest(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    private static BusinessException invalid(){return new BusinessException(HttpStatus.BAD_REQUEST,"MANAGED_EVIDENCE_INPUT_INVALID","Exact bounded content and original action required");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Current owner evidence and association unavailable");}
    private record DeadlineImpl(Instant observedAt,long observedNanos) {}
    private static final class ProgressImpl {boolean documentDispatched,documentCommitted,documentRefused,associationDispatched,associationCommitted,associationRefused,contentVerified;ManagedDocumentEvidencePort.Stored document;ManagedLoanEvidencePort.Attached association;}
}
