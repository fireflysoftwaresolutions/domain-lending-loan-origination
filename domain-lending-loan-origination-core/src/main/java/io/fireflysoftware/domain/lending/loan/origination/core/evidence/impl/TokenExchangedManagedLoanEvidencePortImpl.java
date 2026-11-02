/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import io.fireflysoftware.core.lending.origination.sdk.api.ApplicationEvidenceControllerApi;
import io.fireflysoftware.core.lending.origination.sdk.invoker.ApiClient;
import io.fireflysoftware.core.lending.origination.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
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

/** Genuine observed Core114 SDK only; no synthetic application/status projection or business persistence. */
public final class TokenExchangedManagedLoanEvidencePortImpl implements ManagedLoanEvidencePort {
    static final String ATTACH="lending:applications:evidence:attach",READ="lending:applications:evidence:read";
    private final ManagedLoanExchangeSettings settings;private final ClientRegistration attachRegistration,readRegistration;
    private final ReactiveJwtDecoder decoder;private final WebClientReactiveTokenExchangeTokenResponseClient exchange;private final WebClient transport;
    private final Clock clock;private final LongSupplier nanos;private final AuthenticatedManagedEvidenceAuthorityImpl authority;
    public TokenExchangedManagedLoanEvidencePortImpl(ManagedLoanExchangeSettings settings,ClientRegistration attachRegistration,ClientRegistration readRegistration,ReactiveJwtDecoder decoder,Clock clock,LongSupplier nanos,AuthenticatedManagedEvidenceAuthorityImpl authority){
        this.settings=Objects.requireNonNull(settings);this.attachRegistration=registration(attachRegistration,ATTACH);this.readRegistration=registration(readRegistration,READ);this.decoder=Objects.requireNonNull(decoder);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.authority=Objects.requireNonNull(authority);
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,new org.springframework.web.reactive.function.BodyExtractor.Context(){
            public List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
            public Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
            public Map<String,Object> hints(){var hints=new HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return Map.copyOf(hints);}
        }));
        exchange.addParametersConverter(grant->{var parameters=new LinkedMultiValueMap<String,String>();parameters.set("audience",settings.loanAudience());return parameters;});
    }
    @Override public Mono<Attached> attach(ManagedEvidenceAuthority.Scope scope,ManagedDocumentEvidencePort.Stored document){return Mono.defer(()->{
        var sent=new AtomicBoolean();var known=new AtomicReference<Attached>();
        return client(scope,ATTACH).flatMap(context->same(context.original(),scope).then(Mono.defer(()->{
            requireDocument(scope,document);
            var request=new EvidenceAssociationRequest().actionId(scope.actionId()).applicationId(scope.applicationId()).partyId(scope.partyId()).documentResourceTenantId(scope.documentResourceTenantId()).documentId(document.documentId())
                .fileName(document.fileName()).documentTypeCode(document.documentTypeCode()).mediaType(document.mediaType()).contentLength(document.contentLength()).contentDigest(document.contentDigest()).documentIntentDigest(document.ownerIntentDigest()).documentStoredAt(document.storedAt());
            sent.set(true);
            return new ApplicationEvidenceControllerApi(context.client()).attachManagedApplicationEvidenceWithHttpInfo(scope.applicationId(),scope.actionId(),request).timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).map(response->{
                var value=attached(scope,response.getBody());requireSameDocument(document,value);known.set(value);if(response.getStatusCode().value()!=(value.replayed()?200:201))throw unavailable();return value;
            }).flatMap(value->same(context.original(),scope).thenReturn(value));
        }))).timeout(remaining(scope)).onErrorMap(error->{if(known.get()!=null)return new OutcomeFailure(scope,AssociationOutcome.COMMITTED,known.get());if(!sent.get())return new OutcomeFailure(scope,AssociationOutcome.PROVEN_REFUSED,null);return writeFailure(scope,error);});
    });}
    @Override public Mono<Optional<Attached>> observe(ManagedEvidenceAuthority.Scope scope){return Mono.defer(()->{
        var known=new AtomicReference<Attached>();
        return client(scope,READ).flatMap(context->same(context.original(),scope).then(Mono.defer(()->new ApplicationEvidenceControllerApi(context.client()).observeManagedApplicationEvidenceWithHttpInfo(scope.applicationId(),scope.actionId(),null)))
            .timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).map(response->{
                if(response.getStatusCode().value()==204){if(response.getBody()!=null)throw unavailable();return Optional.<Attached>empty();}
                var value=attached(scope,response.getBody());known.set(value);if(response.getStatusCode().value()!=200||!value.replayed())throw unavailable();return Optional.of(value);
            }).flatMap(value->same(context.original(),scope).thenReturn(value)))
            .timeout(remaining(scope)).onErrorMap(error->known.get()!=null?new OutcomeFailure(scope,AssociationOutcome.COMMITTED,known.get()):safeRead(error));
    });}
    private record ContextImpl(ApiClient client,JwtAuthenticationToken original){@Override public String toString(){return "LoanSdkContext[redacted]";}}
    private Mono<ContextImpl> client(ManagedEvidenceAuthority.Scope scope,String purpose){return authority.requireTransportContext(scope).then(authentication()).flatMap(original->{
        requireSubject(original.getToken(),scope,false,purpose,null);remaining(scope);
        return exchange.getTokenResponse(new TokenExchangeGrantRequest(ATTACH.equals(purpose)?attachRegistration:readRegistration,original.getToken(),null)).timeout(remaining(scope))
            .switchIfEmpty(Mono.error(unavailable())).flatMap(response->{var token=response.getAccessToken();if(token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||!Set.of(purpose).equals(token.getScopes()))return Mono.error(unavailable());
                return decoder.decode(token.getTokenValue()).timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{if(!token.getTokenValue().equals(target.getTokenValue()))throw denied();requireSubject(target,scope,true,purpose,original.getToken());
                    return same(original,scope).then(Mono.fromCallable(()->{var client=new ApiClient(transport);client.setBasePath(settings.loanEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());return new ContextImpl(client,original);}));
                });
            });
    }).timeout(remaining(scope));}
    private ClientRegistration registration(ClientRegistration value,String purpose){Objects.requireNonNull(value);if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(value.getAuthorizationGrantType())||!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(value.getClientAuthenticationMethod())||!Set.of(purpose).equals(value.getScopes())||!settings.loanClientId().equals(value.getClientId())||value.getClientSecret()==null||value.getClientSecret().isBlank()||value.getClientSecret().length()>4096)throw unavailable();LoanIntakeExchangeSettings.endpoint(java.net.URI.create(value.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());return value;}
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(value->value instanceof JwtAuthenticationToken&&value.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(value->value.getPrincipal() instanceof SecurityPrincipal principal&&settings.issuer().equals(principal.issuer())&&settings.platformTenant().equals(principal.tenantId())&&Objects.equals(value.getToken().getSubject(),principal.subject())&&value.getToken().getClaims().equals(principal.claims())&&principal.hasScope(AuthenticatedManagedEvidenceAuthorityImpl.ACTION)).switchIfEmpty(Mono.error(denied()));}
    private Mono<Void> same(JwtAuthenticationToken original,ManagedEvidenceAuthority.Scope scope){return authority.requireTransportContext(scope).then(authentication()).filter(current->current==original).switchIfEmpty(Mono.error(denied())).doOnNext(current->{requireSubject(current.getToken(),scope,false,READ,null);remaining(scope);}).then();}
    private void requireSubject(Jwt jwt,ManagedEvidenceAuthority.Scope scope,boolean target,String purpose,Jwt original){
        Object rawScope=jwt.getClaims().get("scope");Set<String> scopes=rawScope instanceof String text?Set.copyOf(Arrays.asList(text.split(" +"))):Set.of();Instant now=clock.instant();
        if(!settings.loanResourceTenantId().equals(scope.loanResourceTenantId())||!settings.documentResourceTenantId().equals(scope.documentResourceTenantId())||jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||!scope.issuer().equals(jwt.getIssuer().toString())||!scope.subject().equals(jwt.getSubject())||!scope.platformTenant().equals(settings.platformTenant())
            ||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))||!scope.partyId().toString().equals(jwt.getClaims().get("party_id"))||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            ||!List.of(target?settings.loanAudience():settings.incomingAudience()).equals(jwt.getAudience())||!(target?settings.loanClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))
            ||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())||!scopes.contains(target?purpose:AuthenticatedManagedEvidenceAuthorityImpl.ACTION)
            ||target&&(!scopes.equals(Set.of(purpose))||!Objects.equals(original.getSubject(),jwt.getSubject())||!Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id"))||jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
    }
    static Attached attached(ManagedEvidenceAuthority.Scope scope,EvidenceAssociationReceipt value){
        if(value==null||value.getContentLength()==null||value.getDocumentStoredAt()==null||value.getAttachedAt()==null||value.getReplayed()==null)throw unavailable();
        var receipt=new Attached(value.getActionId(),value.getApplicationId(),value.getPartyId(),value.getResourceTenantId(),value.getAssociationId(),value.getDocumentResourceTenantId(),value.getDocumentId(),value.getDocumentTypeId(),value.getDocumentTypeCode(),value.getFileName(),value.getMediaType(),value.getContentLength(),value.getContentDigest(),value.getDocumentIntentDigest(),value.getDocumentStoredAt(),value.getState(),value.getVerificationStatus(),value.getAttachedAt(),value.getReplayed(),value.getIntentDigest());
        if(!scope.actionId().equals(receipt.actionId())||!scope.applicationId().equals(receipt.applicationId())||!scope.partyId().equals(receipt.partyId())||!scope.loanResourceTenantId().equals(receipt.resourceTenantId())||!scope.documentResourceTenantId().equals(receipt.documentResourceTenantId())
            ||!id(receipt.associationId())||!id(receipt.documentId())||!id(receipt.documentTypeId())||!text(receipt.fileName(),200)||receipt.fileName().contains("/")||receipt.fileName().contains("\\")||Set.of(".","..").contains(receipt.fileName())||receipt.documentTypeCode()==null||!receipt.documentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")
            ||receipt.mediaType()==null||receipt.mediaType().length()>100||!receipt.mediaType().matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")||receipt.contentLength()<1||receipt.contentLength()>2097152||!digest(receipt.contentDigest())||!digest(receipt.documentIntentDigest())||!digest(receipt.ownerIntentDigest())||!"ATTACHED".equals(receipt.state())||!"PENDING".equals(receipt.verificationStatus()))throw unavailable();return receipt;
    }
    private static void requireDocument(ManagedEvidenceAuthority.Scope scope,ManagedDocumentEvidencePort.Stored value){
        if(value==null||!scope.actionId().equals(value.actionId())||!scope.applicationId().equals(value.applicationId())||!scope.partyId().equals(value.partyId())||!scope.documentResourceTenantId().equals(value.resourceTenantId())||!id(value.documentId())||value.storedAt()==null||!text(value.fileName(),200)||value.contentLength()<1||value.contentLength()>2097152||!digest(value.contentDigest())||!digest(value.ownerIntentDigest()))throw unavailable();
    }
    private static void requireSameDocument(ManagedDocumentEvidencePort.Stored document,Attached receipt){if(!document.documentId().equals(receipt.documentId())||!document.resourceTenantId().equals(receipt.documentResourceTenantId())||!document.fileName().equals(receipt.fileName())||!document.documentTypeCode().equals(receipt.documentTypeCode())||!document.mediaType().equals(receipt.mediaType())||document.contentLength()!=receipt.contentLength()||!document.contentDigest().equals(receipt.contentDigest())||!document.ownerIntentDigest().equals(receipt.documentIntentDigest())||!document.storedAt().equals(receipt.documentStoredAt()))throw unavailable();}
    static Throwable writeFailure(ManagedEvidenceAuthority.Scope scope,Throwable error){
        if(error instanceof WebClientResponseException http&&http.getStatusCode().value()==503){byte[] bytes=http.getResponseBodyAsByteArray();if(bytes.length>0&&bytes.length<=16384)try{
            var body=new ObjectMapper().readValue(bytes,EvidenceAssociationError.class);if(scope.actionId().equals(body.getActionId())&&"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY".equals(body.getRecovery())&&body.getOutcome()!=null&&("LENDING_EVIDENCE_ASSOCIATION_"+body.getOutcome().getValue()).equals(body.getCode())){
                if(body.getOutcome()==EvidenceAssociationError.OutcomeEnum.REFUSED)return new OutcomeFailure(scope,AssociationOutcome.PROVEN_REFUSED,null);
                if(body.getOutcome()==EvidenceAssociationError.OutcomeEnum.COMMITTED_RESPONSE_UNAVAILABLE)return new OutcomeFailure(scope,AssociationOutcome.COMMITTED,null);
            }
        }catch(Exception malformed){/* An unproven response cannot resolve a dispatched write. */}}
        return new OutcomeFailure(scope,AssociationOutcome.OUTCOME_UNKNOWN,null);
    }
    private Duration remaining(ManagedEvidenceAuthority.Scope scope){long elapsed=nanos.getAsLong()-scope.observedNanos();Duration wall=Duration.between(clock.instant(),scope.notAfter()),mono=Duration.between(scope.observedAt(),scope.notAfter()).minusNanos(elapsed);if(elapsed<0||wall.isZero()||wall.isNegative()||mono.isZero()||mono.isNegative())throw unavailable();return wall.compareTo(mono)<0?wall:mono;}
    private static Throwable safeRead(Throwable error){if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return new ResponseStatusException(status.getStatusCode(),"Current original Loan read authority refused");if(error instanceof WebClientResponseException http&&Set.of(401,403).contains(http.getStatusCode().value()))return new ResponseStatusException(http.getStatusCode(),"Current original Loan read authority refused");return unavailable();}
    private static boolean id(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static boolean text(String value,int maximum){return value!=null&&!value.isBlank()&&value.length()<=maximum&&value.equals(value.strip())&&!value.codePoints().anyMatch(Character::isISOControl);}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original managed evidence authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Original Loan association observation unavailable");}
    @Override public String toString(){return "TokenExchangedManagedLoanEvidencePortImpl[redacted]";}
}
