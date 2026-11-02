/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import io.fireflysoftware.commons.ecm.sdk.api.*;
import io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient;
import io.fireflysoftware.commons.ecm.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Uses only the genuine complete Document70 SDK. Scope values alone cannot authorize transport. */
public final class TokenExchangedManagedDocumentEvidencePortImpl implements ManagedDocumentEvidencePort {
    static final String UPLOAD="document:evidence:upload",READ="document:evidence:read";
    static final int MAX_BYTES=2097152;
    private final ManagedDocumentExchangeSettings settings;private final ClientRegistration uploadRegistration,readRegistration;
    private final ReactiveJwtDecoder decoder;private final WebClientReactiveTokenExchangeTokenResponseClient exchange;private final WebClient transport;
    private final Clock clock;private final LongSupplier nanos;private final AuthenticatedManagedEvidenceAuthorityImpl authority;
    public TokenExchangedManagedDocumentEvidencePortImpl(ManagedDocumentExchangeSettings settings,ClientRegistration uploadRegistration,ClientRegistration readRegistration,ReactiveJwtDecoder decoder,Clock clock,LongSupplier nanos,AuthenticatedManagedEvidenceAuthorityImpl authority){
        this.settings=Objects.requireNonNull(settings);this.uploadRegistration=registration(uploadRegistration,UPLOAD);this.readRegistration=registration(readRegistration,READ);this.decoder=Objects.requireNonNull(decoder);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.authority=Objects.requireNonNull(authority);
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(3*1024*1024);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,new org.springframework.web.reactive.function.BodyExtractor.Context(){
            public List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
            public Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
            public Map<String,Object> hints(){var hints=new HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return Map.copyOf(hints);}
        }));
        exchange.addParametersConverter(grant->{var parameters=new LinkedMultiValueMap<String,String>();parameters.set("audience",settings.documentAudience());return parameters;});
    }
    @Override public Mono<Stored> store(ManagedEvidenceAuthority.Scope scope,ManagedEvidenceUploadRequest request){return Mono.defer(()->{
        var sent=new AtomicBoolean();var known=new AtomicReference<Stored>();
        return client(scope,UPLOAD).flatMap(context->same(context.original(),scope).then(Mono.defer(()->{
            if(!scope.actionId().equals(request.actionId()))throw unavailable();byte[] bytes=request.content();if(bytes.length<1||bytes.length>MAX_BYTES)throw unavailable();
            var body=new EvidenceUploadRequest().actionId(scope.actionId()).applicationId(scope.applicationId()).partyId(scope.partyId()).fileName(request.fileName()).documentTypeCode(request.documentTypeCode()).mediaType(request.mediaType()).content(bytes);
            sent.set(true);
            return new EvidenceUploadControllerApi(context.client()).storeManagedDocumentEvidenceWithHttpInfo(scope.applicationId(),scope.actionId(),body).timeout(remaining(scope))
                .switchIfEmpty(Mono.error(unavailable())).map(response->{var value=stored(scope,response.getBody());if(!request.fileName().equals(value.fileName())||!request.documentTypeCode().equals(value.documentTypeCode())||!request.mediaType().equals(value.mediaType())||bytes.length!=value.contentLength()||!hash(bytes).equals(value.contentDigest()))throw unavailable();known.set(value);if(response.getStatusCode().value()!=(value.replayed()?200:201))throw unavailable();return value;})
                .flatMap(value->same(context.original(),scope).thenReturn(value));
        }))).timeout(remaining(scope)).onErrorMap(error->{if(known.get()!=null)return new OutcomeFailure(scope,CustodyOutcome.COMMITTED,known.get());if(!sent.get())return new OutcomeFailure(scope,CustodyOutcome.PROVEN_REFUSED,null);return writeFailure(scope,error);});
    });}
    @Override public Mono<Optional<Stored>> observe(ManagedEvidenceAuthority.Scope scope){return Mono.defer(()->{
        var known=new AtomicReference<Stored>();
        return client(scope,READ).flatMap(context->same(context.original(),scope).then(Mono.defer(()->new EvidenceReadControllerApi(context.client()).observeManagedDocumentEvidenceWithHttpInfo(scope.applicationId(),scope.actionId(),null)))
            .timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).map(response->{var value=stored(scope,response.getBody());known.set(value);if(response.getStatusCode().value()!=200||!value.replayed())throw unavailable();return value;})
            .flatMap(value->same(context.original(),scope).thenReturn(Optional.of(value))))
            .timeout(remaining(scope)).onErrorMap(error->known.get()!=null?new OutcomeFailure(scope,CustodyOutcome.COMMITTED,known.get()):safeRead(error));
    });}
    @Override public Mono<Content> readContent(ManagedEvidenceAuthority.Scope scope,Stored receipt){return Mono.defer(()->{
        requireStored(scope,receipt);
        return client(scope,READ).flatMap(context->same(context.original(),scope).then(Mono.defer(()->new EvidenceReadControllerApi(context.client()).readManagedDocumentEvidenceContentWithHttpInfo(scope.applicationId(),scope.actionId(),null)))
            .timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).flatMap(response->content(scope,receipt,response))
            .flatMap(value->same(context.original(),scope).thenReturn(value))).timeout(remaining(scope)).onErrorMap(TokenExchangedManagedDocumentEvidencePortImpl::safeRead);
    });}
    private record ContextImpl(ApiClient client,JwtAuthenticationToken original){@Override public String toString(){return "DocumentSdkContext[redacted]";}}
    private Mono<ContextImpl> client(ManagedEvidenceAuthority.Scope scope,String purpose){return authority.requireTransportContext(scope).then(authentication()).flatMap(original->{
        requireSubject(original.getToken(),scope,false,purpose,null);remaining(scope);
        return exchange.getTokenResponse(new TokenExchangeGrantRequest(UPLOAD.equals(purpose)?uploadRegistration:readRegistration,original.getToken(),null)).timeout(remaining(scope))
            .switchIfEmpty(Mono.error(unavailable())).flatMap(response->{var token=response.getAccessToken();if(token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||!Set.of(purpose).equals(token.getScopes()))return Mono.error(unavailable());
                return decoder.decode(token.getTokenValue()).timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{if(!token.getTokenValue().equals(target.getTokenValue()))throw denied();requireSubject(target,scope,true,purpose,original.getToken());
                    return same(original,scope).then(Mono.fromCallable(()->{var client=new ApiClient(transport);client.setBasePath(settings.documentEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());return new ContextImpl(client,original);}));
                });
            });
    }).timeout(remaining(scope));}
    private ClientRegistration registration(ClientRegistration value,String purpose){Objects.requireNonNull(value);if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(value.getAuthorizationGrantType())||!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(value.getClientAuthenticationMethod())||!Set.of(purpose).equals(value.getScopes())||!settings.documentClientId().equals(value.getClientId())||value.getClientSecret()==null||value.getClientSecret().isBlank()||value.getClientSecret().length()>4096)throw unavailable();LoanIntakeExchangeSettings.endpoint(java.net.URI.create(value.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());return value;}
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(value->value instanceof JwtAuthenticationToken&&value.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(value->value.getPrincipal() instanceof SecurityPrincipal principal&&settings.issuer().equals(principal.issuer())&&settings.platformTenant().equals(principal.tenantId())&&Objects.equals(value.getToken().getSubject(),principal.subject())&&value.getToken().getClaims().equals(principal.claims())&&principal.hasScope(AuthenticatedManagedEvidenceAuthorityImpl.ACTION)).switchIfEmpty(Mono.error(denied()));}
    private Mono<Void> same(JwtAuthenticationToken original,ManagedEvidenceAuthority.Scope scope){return authority.requireTransportContext(scope).then(authentication()).filter(current->current==original).switchIfEmpty(Mono.error(denied())).doOnNext(current->{requireSubject(current.getToken(),scope,false,READ,null);remaining(scope);}).then();}
    private void requireSubject(Jwt jwt,ManagedEvidenceAuthority.Scope scope,boolean target,String purpose,Jwt original){
        Object rawScope=jwt.getClaims().get("scope");Set<String> scopes=rawScope instanceof String text?Set.copyOf(Arrays.asList(text.split(" +"))):Set.of();Instant now=clock.instant();
        if(!settings.documentResourceTenantId().equals(scope.documentResourceTenantId())||jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||!scope.issuer().equals(jwt.getIssuer().toString())||!scope.subject().equals(jwt.getSubject())||!scope.platformTenant().equals(settings.platformTenant())
            ||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))||!scope.partyId().toString().equals(jwt.getClaims().get("party_id"))||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            ||!List.of(target?settings.documentAudience():settings.incomingAudience()).equals(jwt.getAudience())||!(target?settings.documentClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))
            ||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())||!scopes.contains(target?purpose:AuthenticatedManagedEvidenceAuthorityImpl.ACTION)
            ||target&&(!scopes.equals(Set.of(purpose))||!Objects.equals(original.getSubject(),jwt.getSubject())||!Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id"))||jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
    }
    static Stored stored(ManagedEvidenceAuthority.Scope scope,EvidenceUploadReceipt value){
        if(value==null||value.getContentLength()==null||value.getStoredAt()==null||value.getReplayed()==null)throw unavailable();Object rawStoredAt=value.getStoredAt();if(!(rawStoredAt instanceof Instant storedAt))throw unavailable();
        var receipt=new Stored(value.getActionId(),value.getApplicationId(),value.getPartyId(),value.getResourceTenantId(),value.getDocumentId(),value.getFileName(),value.getDocumentTypeCode(),value.getMediaType(),value.getContentLength(),value.getContentDigest(),value.getIntentDigest(),storedAt,value.getReplayed());requireStored(scope,receipt);return receipt;
    }
    private static void requireStored(ManagedEvidenceAuthority.Scope scope,Stored receipt){if(receipt==null||!scope.actionId().equals(receipt.actionId())||!scope.applicationId().equals(receipt.applicationId())||!scope.partyId().equals(receipt.partyId())||!scope.documentResourceTenantId().equals(receipt.resourceTenantId())||receipt.documentId()==null||receipt.documentId().equals(new UUID(0,0))||receipt.contentLength()<1||receipt.contentLength()>MAX_BYTES||receipt.contentDigest()==null||!receipt.contentDigest().matches("[a-f0-9]{64}")||receipt.ownerIntentDigest()==null||!receipt.ownerIntentDigest().matches("[a-f0-9]{64}")||receipt.storedAt()==null||receipt.fileName()==null||receipt.fileName().isBlank()||receipt.documentTypeCode()==null||!receipt.documentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")||receipt.mediaType()==null)throw unavailable();}
    static Mono<Content> content(ManagedEvidenceAuthority.Scope scope,Stored receipt,ResponseEntity<Resource> response){return Mono.fromCallable(()->{
        requireStored(scope,receipt);if(response.getStatusCode().value()!=200||response.getBody()==null||response.getHeaders().getContentLength()!=receipt.contentLength()||!MediaType.APPLICATION_OCTET_STREAM.equals(response.getHeaders().getContentType()))throw unavailable();
        var digestHeaders=response.getHeaders().get("X-Evidence-Content-SHA256");if(digestHeaders==null||digestHeaders.size()!=1||!receipt.contentDigest().equals(digestHeaders.getFirst()))throw unavailable();
        byte[] bytes;try(var stream=response.getBody().getInputStream()){bytes=stream.readNBytes(MAX_BYTES+1);}if(bytes.length!=receipt.contentLength()||!receipt.contentDigest().equals(hash(bytes)))throw unavailable();
        return new Content(scope.actionId(),receipt.documentId(),bytes.length,hash(bytes));
    }).subscribeOn(Schedulers.boundedElastic());}
    static Throwable writeFailure(ManagedEvidenceAuthority.Scope scope,Throwable error){
        if(error instanceof WebClientResponseException http&&http.getStatusCode().value()==503){byte[] bytes=http.getResponseBodyAsByteArray();if(bytes.length>0&&bytes.length<=16384)try{
            var body=new ObjectMapper().readValue(bytes,EvidenceUploadError.class);if(scope.actionId().equals(body.getActionId())&&"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY".equals(body.getRecovery())&&body.getOutcome()!=null&&("DOCUMENT_EVIDENCE_"+body.getOutcome().getValue()).equals(body.getCode())){
                if(body.getOutcome()==EvidenceUploadError.OutcomeEnum.REFUSED)return new OutcomeFailure(scope,CustodyOutcome.PROVEN_REFUSED,null);
                if(body.getOutcome()==EvidenceUploadError.OutcomeEnum.COMMITTED_RESPONSE_UNAVAILABLE)return new OutcomeFailure(scope,CustodyOutcome.COMMITTED,null);
            }
        }catch(Exception malformed){/* Unproven error bodies cannot resolve a dispatched write. */}}
        return new OutcomeFailure(scope,CustodyOutcome.OUTCOME_UNKNOWN,null);
    }
    private Duration remaining(ManagedEvidenceAuthority.Scope scope){long elapsed=nanos.getAsLong()-scope.observedNanos();Duration wall=Duration.between(clock.instant(),scope.notAfter()),mono=Duration.between(scope.observedAt(),scope.notAfter()).minusNanos(elapsed);if(elapsed<0||wall.isZero()||wall.isNegative()||mono.isZero()||mono.isNegative())throw unavailable();return wall.compareTo(mono)<0?wall:mono;}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw unavailable();}}
    private static Throwable safeRead(Throwable error){if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return new ResponseStatusException(status.getStatusCode(),"Current original document read authority refused");if(error instanceof WebClientResponseException http&&Set.of(401,403).contains(http.getStatusCode().value()))return new ResponseStatusException(http.getStatusCode(),"Current original document read authority refused");return unavailable();}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original managed evidence authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Original document custody observation unavailable");}
    @Override public String toString(){return "TokenExchangedManagedDocumentEvidencePortImpl[redacted]";}
}
