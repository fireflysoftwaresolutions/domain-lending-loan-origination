/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.intake.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.*;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import io.fireflysoftware.core.lending.origination.sdk.api.DraftLoanIntakesApi;
import io.fireflysoftware.core.lending.origination.sdk.api.ApplicationPartyEmploymentApi;
import io.fireflysoftware.core.lending.origination.sdk.api.ApplicationPartyQueryApi;
import io.fireflysoftware.core.lending.origination.sdk.model.ApplicationPartyDTO;
import io.fireflysoftware.core.lending.origination.sdk.invoker.ApiClient;
import io.fireflysoftware.core.lending.origination.sdk.model.DraftIntakeRequest;
import io.fireflysoftware.core.lending.origination.sdk.model.DraftIntakeReceipt;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.fireflyframework.web.error.exceptions.BusinessException;
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

/** Per-operation native token exchange preserves the verified HUMAN subject and signed party projection. */
public final class TokenExchangedCoreDraftIntakePortImpl implements CoreDraftIntakePort {
    private static final String SCOPE="lending:applications:draft:create";
    private static final int BORROWER_VIEW_LIMIT=64;
    private final LoanIntakeExchangeSettings settings;
    private final ClientRegistration registration;
    private final ReactiveJwtDecoder targetDecoder;
    private final WebClientReactiveTokenExchangeTokenResponseClient exchange;
    private final WebClient transport;
    private final Clock clock;
    public TokenExchangedCoreDraftIntakePortImpl(LoanIntakeExchangeSettings settings,ClientRegistration registration,ReactiveJwtDecoder targetDecoder,Clock clock){
        this.settings=Objects.requireNonNull(settings);this.registration=Objects.requireNonNull(registration);this.targetDecoder=Objects.requireNonNull(targetDecoder);this.clock=Objects.requireNonNull(clock);
        if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(registration.getAuthorizationGrantType())
            || !ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(registration.getClientAuthenticationMethod())
            || !Set.of(SCOPE).equals(registration.getScopes()) || !settings.coreClientId().equals(registration.getClientId())
            || registration.getClientSecret()==null || registration.getClientSecret().isBlank() || registration.getClientSecret().length()>4096)throw unavailable();
        LoanIntakeExchangeSettings.endpoint(java.net.URI.create(registration.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,
            new org.springframework.web.reactive.function.BodyExtractor.Context(){
                public java.util.List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
                public java.util.Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
                public java.util.Map<String,Object> hints(){var hints=new java.util.HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return java.util.Map.copyOf(hints);}
            }));
        exchange.addParametersConverter(grant->{var values=new LinkedMultiValueMap<String,String>();values.set("audience",settings.coreAudience());return values;});
    }
    @Override public Mono<DraftLoanIntakeReceipt> create(DraftLoanIntakeRequest supplied){
        final DraftLoanIntakeRequest request;
        try{request=capture(supplied);}catch(RuntimeException invalid){return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact draft intake request required"));}
        return Mono.defer(()->{Instant started=clock.instant();long before=System.nanoTime();
            var known=new java.util.concurrent.atomic.AtomicReference<DraftLoanIntakeReceipt>();
            var originalAuthority=new java.util.concurrent.atomic.AtomicReference<JwtAuthenticationToken>();
            var attempted=new java.util.concurrent.atomic.AtomicBoolean();
            return authentication().flatMap(original->{originalAuthority.set(original);Jwt subject=original.getToken();requireSubject(subject,request,false,null);
                return exchange.getTokenResponse(new TokenExchangeGrantRequest(registration,subject,null))
                    .timeout(remaining(started,before)).switchIfEmpty(Mono.error(unavailable())).flatMap(response->{
                        var token=response.getAccessToken();
                        if(token==null || !OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType()) || !Set.of(SCOPE).equals(token.getScopes()))return Mono.error(unavailable());
                        return targetDecoder.decode(token.getTokenValue()).timeout(remaining(started,before)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{
                            if(!token.getTokenValue().equals(target.getTokenValue()))return Mono.error(unavailable());requireSubject(target,request,true,subject);
                            return same(original,request).then(Mono.defer(()->{
                                var client=new ApiClient(transport);client.setBasePath(settings.coreEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());
                                var body=new DraftIntakeRequest().requestId(request.requestId()).simulationId(request.simulationId()).productId(request.productId())
                                    .partyId(request.partyId()).requestedAmount(request.requestedAmount()).term(request.term()).currency(request.currency()).purpose(request.purpose());
                                attempted.set(true);
                                return new DraftLoanIntakesApi(client).createDraftLoanIntake(body).timeout(remaining(started,before))
                                    .switchIfEmpty(Mono.error(unavailable())).map(value->receipt(request,value)).doOnNext(known::set)
                                    .flatMap(value->borrowerViews(client,request,value,original,target,subject,started,before)
                                        .then(same(original,request)).then(Mono.fromCallable(()->{remaining(started,before);return value;})));
                            }));
                        });
                    });
            }).timeout(remaining(started,before)).onErrorResume(error->{
                var completed=known.get();
                if(completed==null)return Mono.error(attempted.get()&&uncertain(error)?outcomeUnknown(request):safe(error));
                return same(originalAuthority.get(),request).thenReturn(true).onErrorReturn(false)
                    .flatMap(current->Mono.error(completedUnavailable(request,completed,current)));
            });
        });
    }
    private Mono<Void> borrowerViews(ApiClient client,DraftLoanIntakeRequest request,DraftLoanIntakeReceipt receipt,JwtAuthenticationToken original,Jwt target,Jwt subject,Instant started,long before){
        return same(original,request).then(Mono.defer(()->{
            remaining(started,before);requireSubject(target,request,true,subject);
            return new ApplicationPartyEmploymentApi(client).findPrimaryApplicationParty(receipt.applicationId(),request.requestId().toString())
                .timeout(remaining(started,before)).switchIfEmpty(Mono.error(unavailable())).doOnNext(row->requirePrimary(request,receipt,row))
                .flatMap(primary->same(original,request).then(Mono.defer(()->{
                    remaining(started,before);requireSubject(target,request,true,subject);
                    return new ApplicationPartyQueryApi(client).findByPartyId(request.partyId(),request.requestId().toString())
                        .take(BORROWER_VIEW_LIMIT+1).collectList()
                        .timeout(remaining(started,before)).doOnNext(rows->requireBorrowerView(request,receipt,primary,rows)).then();
                })));
        })).then();
    }
    static void requirePrimary(DraftLoanIntakeRequest request,DraftLoanIntakeReceipt receipt,ApplicationPartyDTO row){
        if(row==null || !receipt.applicationId().equals(row.getLoanApplicationId()) || !request.partyId().equals(row.getPartyId())
            || !Boolean.TRUE.equals(row.getIsPrimary()) || row.getApplicationPartyId()==null || row.getApplicationPartyId().equals(new UUID(0,0))
            || row.getRoleCodeId()==null || row.getRoleCodeId().equals(new UUID(0,0)))throw unavailable();
    }
    static void requireBorrowerView(DraftLoanIntakeRequest request,DraftLoanIntakeReceipt receipt,ApplicationPartyDTO primary,List<ApplicationPartyDTO> rows){
        requirePrimary(request,receipt,primary);if(rows==null || rows.size()>BORROWER_VIEW_LIMIT)throw unavailable();
        for(var row:rows)if(row==null || !request.partyId().equals(row.getPartyId()))throw unavailable();
        var matching=rows.stream().filter(row->receipt.applicationId().equals(row.getLoanApplicationId())&&Boolean.TRUE.equals(row.getIsPrimary())).toList();
        if(matching.size()!=1)throw unavailable();var row=matching.getFirst();requirePrimary(request,receipt,row);
        if(!primary.getApplicationPartyId().equals(row.getApplicationPartyId()) || !primary.getRoleCodeId().equals(row.getRoleCodeId()))throw unavailable();
    }
    static BusinessException completedUnavailable(DraftLoanIntakeRequest request,DraftLoanIntakeReceipt receipt,boolean currentAuthority){
        var metadata=new HashMap<String,Object>();metadata.put("requestId",request.requestId().toString());metadata.put("outcome","COMMITTED");metadata.put("recovery","SAME_REQUEST_ID_EXACT_REPLAY");
        if(currentAuthority)metadata.put("intentDigest",receipt.intentDigest());
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"LENDING_INTAKE_COMMITTED_RESPONSE_UNAVAILABLE","Draft intake committed; borrower view response unavailable. Recover using the same request ID and exact original terms.",metadata);
    }
    private static BusinessException outcomeUnknown(DraftLoanIntakeRequest request){
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"LENDING_INTAKE_OUTCOME_UNKNOWN","Draft intake outcome unavailable. Recover using the same request ID and exact original terms.",
            Map.of("requestId",request.requestId().toString(),"outcome","UNKNOWN","recovery","SAME_REQUEST_ID_EXACT_REPLAY"));
    }
    private static boolean uncertain(Throwable error){
        if(error instanceof WebClientResponseException http)return !Set.of(400,401,403,404,409,422).contains(http.getStatusCode().value());
        if(error instanceof ResponseStatusException status)return !Set.of(400,401,403,404,409,422).contains(status.getStatusCode().value());return true;
    }
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(c->c.getAuthentication())
        .filter(a->a instanceof JwtAuthenticationToken && a.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(a->a.getPrincipal() instanceof org.fireflyframework.security.api.domain.SecurityPrincipal p
            && settings.issuer().equals(p.issuer()) && settings.platformTenant().equals(p.tenantId())
            && Objects.equals(a.getToken().getSubject(),p.subject()) && a.getToken().getClaims().equals(p.claims()) && p.hasScope(SCOPE))
        .switchIfEmpty(Mono.error(denied()));}
    private Mono<Void> same(JwtAuthenticationToken original,DraftLoanIntakeRequest request){return authentication().filter(current->current==original)
        .switchIfEmpty(Mono.error(denied())).doOnNext(current->requireSubject(current.getToken(),request,false,null)).then();}
    private void requireSubject(Jwt jwt,DraftLoanIntakeRequest request,boolean target,Jwt original){
        Object rawScope=jwt.getClaims().get("scope");Set<String> scopes=rawScope instanceof String value?Set.copyOf(Arrays.asList(value.split(" +"))):Set.of();
        Instant now=clock.instant();
        if(jwt.getIssuer()==null || !settings.issuer().equals(jwt.getIssuer().toString()) || jwt.getSubject()==null || jwt.getSubject().isBlank()
            || !"HUMAN".equals(jwt.getClaims().get("principal_kind")) || !request.partyId().toString().equals(jwt.getClaims().get("party_id"))
            || !settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            || !List.of(target?settings.coreAudience():settings.incomingAudience()).equals(jwt.getAudience())
            || !(target?settings.coreClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))
            || jwt.getExpiresAt()==null || jwt.getNotBefore()==null || !now.isBefore(jwt.getExpiresAt()) || now.isBefore(jwt.getNotBefore())
            || !scopes.contains(SCOPE) || target && (!scopes.equals(Set.of(SCOPE)) || !Objects.equals(original.getSubject(),jwt.getSubject())
                || !Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id")) || jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
    }
    private static DraftLoanIntakeRequest capture(DraftLoanIntakeRequest r){
        if(r==null)throw new IllegalArgumentException();for(UUID id:new UUID[]{r.requestId(),r.simulationId(),r.productId(),r.partyId()})
            if(id==null || id.equals(new UUID(0,0)))throw new IllegalArgumentException();
        var amount=Objects.requireNonNull(r.requestedAmount()).setScale(2,RoundingMode.UNNECESSARY);
        if(amount.signum()<=0 || amount.precision()>19 || r.term()==null || r.term()<1 || r.currency()==null || !r.currency().matches("[A-Z]{3}"))throw new IllegalArgumentException();
        if(r.purpose()!=null){
            if(r.purpose().indexOf(0)>=0 || r.purpose().codePointCount(0,r.purpose().length())>50)throw new IllegalArgumentException();
            try{java.nio.charset.StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(r.purpose()));}
            catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException();}
        }
        return new DraftLoanIntakeRequest(r.requestId(),r.simulationId(),r.productId(),r.partyId(),amount,r.term(),r.currency(),r.purpose());
    }
    static DraftLoanIntakeReceipt receipt(DraftLoanIntakeRequest r,DraftIntakeReceipt v){
        if(v.getApplicationId()==null || v.getApplicationId().equals(new UUID(0,0)) || !r.requestId().equals(v.getRequestId()) || !r.simulationId().equals(v.getSimulationId())
            || !r.productId().equals(v.getProductId()) || !r.partyId().equals(v.getPartyId()) || v.getRequestedAmount()==null || r.requestedAmount().compareTo(v.getRequestedAmount())!=0
            || !r.term().equals(v.getTerm()) || !r.currency().equals(v.getCurrency()) || !Objects.equals(r.purpose(),v.getPurpose())
            || !"DRAFT".equals(v.getInitialStatusCode()) || !"PENDING_DOCUMENTS".equals(v.getInitialSubStatusCode()) || v.getCreatedAt()==null || v.getReplayed()==null
            || v.getIntentDigest()==null || !v.getIntentDigest().matches("[0-9a-f]{64}"))throw unavailable();
        return new DraftLoanIntakeReceipt(v.getRequestId(),v.getApplicationId(),v.getSimulationId(),v.getProductId(),v.getPartyId(),v.getRequestedAmount(),
            v.getTerm(),v.getCurrency(),v.getPurpose(),v.getInitialStatusCode(),v.getInitialSubStatusCode(),v.getCreatedAt().toInstant(ZoneOffset.UTC),v.getReplayed(),v.getIntentDigest());
    }
    private Duration remaining(Instant started,long before){long elapsed=System.nanoTime()-before;var wall=Duration.between(clock.instant(),started.plusSeconds(5));var mono=Duration.ofSeconds(5).minusNanos(elapsed);
        if(elapsed<0 || wall.isZero() || wall.isNegative() || mono.isZero() || mono.isNegative())throw unavailable();return wall.compareTo(mono)<0?wall:mono;}
    private static Throwable safe(Throwable error){
        if(error instanceof ResponseStatusException status)return new ResponseStatusException(status.getStatusCode(),"Lending intake owner is unavailable");
        if(error instanceof WebClientResponseException http){int code=http.getStatusCode().value();return new ResponseStatusException(Set.of(400,401,403,404,409,422,503).contains(code)?HttpStatus.valueOf(code):HttpStatus.SERVICE_UNAVAILABLE,"Lending intake owner is unavailable");}
        return unavailable();
    }
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current lending party authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Authenticated lending intake owner unavailable");}
    @Override public String toString(){return "TokenExchangedCoreDraftIntakePortImpl[redacted]";}
}
