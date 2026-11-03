/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import io.fireflysoftware.core.contract.sdk.api.ContractLinkControllerApi;
import io.fireflysoftware.core.contract.sdk.invoker.ApiClient;
import io.fireflysoftware.core.contract.sdk.model.*;
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

/** Actual Core Contract54 SDK; original action and typed persisted receipt, without direct business storage. */
public final class TokenExchangedInitialLoanContractOwnerPortImpl implements InitialLoanContractOwnerPort {
    static final String LINK="contracts:loan:initial-primary-link:write",READ="contracts:loan:initial-primary-link:read";
    private final InitialLoanContractExchangeSettings settings;private final ClientRegistration linkRegistration,readRegistration;
    private final ReactiveJwtDecoder decoder;private final WebClientReactiveTokenExchangeTokenResponseClient exchange;private final WebClient transport;
    private final Clock clock;private final LongSupplier nanos;private final InitialLoanContractAuthority authority;
    public TokenExchangedInitialLoanContractOwnerPortImpl(InitialLoanContractExchangeSettings settings,ClientRegistration linkRegistration,ClientRegistration readRegistration,ReactiveJwtDecoder decoder,Clock clock,LongSupplier nanos,InitialLoanContractAuthority authority){
        this.settings=Objects.requireNonNull(settings);this.linkRegistration=registration(linkRegistration,LINK);this.readRegistration=registration(readRegistration,READ);this.decoder=Objects.requireNonNull(decoder);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.authority=Objects.requireNonNull(authority);
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,new org.springframework.web.reactive.function.BodyExtractor.Context(){
            public List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
            public Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
            public Map<String,Object> hints(){var hints=new HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return Map.copyOf(hints);}
        }));
        exchange.addParametersConverter(grant->{var parameters=new LinkedMultiValueMap<String,String>();parameters.set("audience",settings.contractAudience());return parameters;});
    }
    @Override public Mono<InitialLoanContractReceipt> link(InitialLoanContractAuthority.Scope scope,InitialLoanContractBasis basis,UUID existing){return Mono.defer(()->{
        var sent=new AtomicBoolean();var known=new AtomicReference<InitialLoanContractReceipt>();
        return client(scope,LINK).flatMap(context->same(context.original(),scope).then(Mono.defer(()->{
            if(scope.purpose()!=InitialLoanContractAuthority.Purpose.LINK||!scope.applicationId().equals(basis.applicationId())||!scope.partyId().equals(basis.partyId())||!scope.loanResourceTenantId().equals(basis.resourceTenantId()))throw unavailable();
            var request=new ContractLinkRequest().actionId(scope.actionId()).applicationId(scope.applicationId()).existingContractId(existing).productId(basis.productId()).currencyCode(basis.currencyCode());
            sent.set(true);
            return new ContractLinkControllerApi(context.client()).linkInitialPrimaryLoanContractWithHttpInfo(scope.applicationId(),scope.actionId(),request).switchIfEmpty(Mono.error(unavailable())).map(response->{
                var value=InitialLoanContractOrchestratorImpl.receipt(scope,basis,existing,receipt(scope,response.getBody()));known.set(value);if(response.getStatusCode().value()!=(value.replayed()?200:201))throw unavailable();return value;
            }).flatMap(value->same(context.original(),scope).thenReturn(value));
        }))).timeout(remaining(scope)).onErrorMap(error->{if(known.get()!=null)return failure(scope,InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);if(!sent.get())return failure(scope,InitialLoanContractFailure.Outcome.REFUSED);return writeFailure(scope,error);});
    });}
    @Override public Mono<Optional<InitialLoanContractReceipt>> observe(InitialLoanContractAuthority.Scope scope){return Mono.defer(()->{
        var known=new AtomicReference<InitialLoanContractReceipt>();
        return client(scope,READ).flatMap(context->same(context.original(),scope).then(Mono.defer(()->new ContractLinkControllerApi(context.client()).observeInitialPrimaryLoanContractWithHttpInfo(scope.applicationId(),scope.actionId(),null)))
            .switchIfEmpty(Mono.error(unavailable())).map(response->{
                if(response.getStatusCode().value()==204){if(response.getBody()!=null)throw unavailable();return Optional.<InitialLoanContractReceipt>empty();}
                var value=receipt(scope,response.getBody());known.set(value);if(response.getStatusCode().value()!=200||!value.replayed())throw unavailable();return Optional.of(value);
            }).flatMap(value->same(context.original(),scope).thenReturn(value))).timeout(remaining(scope))
            .onErrorMap(error->known.get()!=null?failure(scope,InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE):safeRead(scope,error));
    });}
    private record ContextImpl(ApiClient client,JwtAuthenticationToken original){@Override public String toString(){return "ContractSdkContext[redacted]";}}
    private Mono<ContextImpl> client(InitialLoanContractAuthority.Scope scope,String purpose){return authority.requireTransportContext(scope).then(authentication(scope)).flatMap(original->{
        requireSubject(original.getToken(),scope,false,purpose,null);remaining(scope);
        return exchange.getTokenResponse(new TokenExchangeGrantRequest(LINK.equals(purpose)?linkRegistration:readRegistration,original.getToken(),null)).timeout(remaining(scope))
            .switchIfEmpty(Mono.error(unavailable())).flatMap(response->{var token=response.getAccessToken();if(token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||!Set.of(purpose).equals(token.getScopes()))return Mono.error(unavailable());
                return decoder.decode(token.getTokenValue()).timeout(remaining(scope)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{if(!token.getTokenValue().equals(target.getTokenValue()))throw denied();requireSubject(target,scope,true,purpose,original.getToken());
                    return same(original,scope).then(Mono.fromCallable(()->{var client=new ApiClient(transport);client.setBasePath(settings.contractEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());return new ContextImpl(client,original);}));
                });
            });
    }).timeout(remaining(scope));}
    private ClientRegistration registration(ClientRegistration value,String purpose){Objects.requireNonNull(value);if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(value.getAuthorizationGrantType())||!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(value.getClientAuthenticationMethod())||!Set.of(purpose).equals(value.getScopes())||!settings.contractClientId().equals(value.getClientId())||value.getClientSecret()==null||value.getClientSecret().isBlank()||value.getClientSecret().length()>4096)throw unavailable();LoanIntakeExchangeSettings.endpoint(java.net.URI.create(value.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());return value;}
    private Mono<JwtAuthenticationToken> authentication(InitialLoanContractAuthority.Scope scope){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(value->value instanceof JwtAuthenticationToken&&value.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(value->value.getPrincipal() instanceof SecurityPrincipal principal&&settings.issuer().equals(principal.issuer())&&settings.platformTenant().equals(principal.tenantId())&&Objects.equals(value.getToken().getSubject(),principal.subject())&&value.getToken().getClaims().equals(principal.claims())&&principal.hasScope(InitialLoanContractAuthority.action(scope.purpose()))).switchIfEmpty(Mono.error(denied()));}
    private Mono<Void> same(JwtAuthenticationToken original,InitialLoanContractAuthority.Scope scope){return authority.requireTransportContext(scope).then(authentication(scope)).filter(current->current==original).switchIfEmpty(Mono.error(denied())).doOnNext(current->{requireSubject(current.getToken(),scope,false,READ,null);remaining(scope);}).then();}
    private void requireSubject(Jwt jwt,InitialLoanContractAuthority.Scope scope,boolean target,String purpose,Jwt original){
        Object rawScope=jwt.getClaims().get("scope");Set<String> scopes=rawScope instanceof String text?Set.copyOf(Arrays.asList(text.split(" +"))):Set.of();Instant now=clock.instant();
        if(!settings.loanResourceTenantId().equals(scope.loanResourceTenantId())||!settings.contractResourceTenantId().equals(scope.contractResourceTenantId())||jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||!scope.issuer().equals(jwt.getIssuer().toString())||!scope.subject().equals(jwt.getSubject())||!scope.platformTenant().equals(settings.platformTenant())
            ||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))||!scope.partyId().toString().equals(jwt.getClaims().get("party_id"))||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            ||!List.of(target?settings.contractAudience():settings.incomingAudience()).equals(jwt.getAudience())||!(target?settings.contractClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))
            ||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())||!scopes.contains(target?purpose:InitialLoanContractAuthority.action(scope.purpose()))
            ||target&&(!scopes.equals(Set.of(purpose))||!Objects.equals(original.getSubject(),jwt.getSubject())||!Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id"))||jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
    }
    InitialLoanContractReceipt receipt(InitialLoanContractAuthority.Scope scope,ContractLinkReceipt value){
        if(value==null||value.getPurpose()==null||value.getContractStatusAtLink()==null||value.getContractCreated()==null||value.getReplayed()==null||!settings.roleInContractId().equals(value.getRoleInContractId()))throw unavailable();
        var receipt=new InitialLoanContractReceipt(value.getActionId(),value.getApplicationId(),value.getResourceTenantId(),value.getPartyId(),value.getRoleInContractId(),value.getContractId(),value.getContractPartyId(),value.getProductId(),value.getCurrencyCode(),value.getPurpose().getValue(),value.getContractStatusAtLink().getValue(),value.getContractCreated(),value.getLinkedAt(),value.getIntentDigest(),value.getReplayed());
        InitialLoanContractOrchestratorImpl.requireIdentity(scope,receipt);return receipt;
    }
    static InitialLoanContractFailure writeFailure(InitialLoanContractAuthority.Scope scope,Throwable error){
        if(error instanceof WebClientResponseException http&&Set.of(409,503).contains(http.getStatusCode().value())){
            var type=http.getHeaders().getContentType();byte[] bytes=http.getResponseBodyAsByteArray();
            if(type!=null&&MediaType.APPLICATION_JSON.isCompatibleWith(type)&&bytes.length>0&&bytes.length<=16384)try{
                var mapper=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
                var body=mapper.readValue(bytes,ContractLinkError.class);
                if(scope.actionId().equals(body.getActionId())&&"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY".equals(body.getRecovery())&&body.getOutcome()!=null){
                    var outcome=InitialLoanContractFailure.Outcome.valueOf(body.getOutcome().getValue());
                    if(http.getStatusCode().value()==409&&outcome==InitialLoanContractFailure.Outcome.CONFLICT&&Set.of("CONTRACT_LINK_ACTION_CONFLICT","CONTRACT_LINK_INITIAL_PRIMARY_ALREADY_BOUND").contains(body.getCode()))return failure(scope,outcome);
                    if(http.getStatusCode().value()==503&&("CONTRACT_LINK_"+outcome.name()).equals(body.getCode())&&Set.of(InitialLoanContractFailure.Outcome.REFUSED,InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE).contains(outcome))return failure(scope,outcome);
                }
            }catch(Exception malformed){/* An ambiguous envelope cannot resolve a dispatched original action. */}
        }
        return failure(scope,InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN);
    }
    private static InitialLoanContractFailure failure(InitialLoanContractAuthority.Scope scope,InitialLoanContractFailure.Outcome outcome){return new InitialLoanContractFailure(scope.actionId(),outcome);}
    private Duration remaining(InitialLoanContractAuthority.Scope scope){long elapsed=nanos.getAsLong()-scope.observedNanos();Duration wall=Duration.between(clock.instant(),scope.notAfter()),mono=Duration.between(scope.observedAt(),scope.notAfter()).minusNanos(elapsed);if(elapsed<0||wall.isZero()||wall.isNegative()||mono.isZero()||mono.isNegative())throw unavailable();return wall.compareTo(mono)<0?wall:mono;}
    private static Throwable safeRead(InitialLoanContractAuthority.Scope scope,Throwable error){if(error instanceof ResponseStatusException status&&Set.of(401,403).contains(status.getStatusCode().value()))return new ResponseStatusException(status.getStatusCode(),"Current original Contract read authority refused");if(error instanceof WebClientResponseException http&&Set.of(401,403).contains(http.getStatusCode().value()))return new ResponseStatusException(http.getStatusCode(),"Current original Contract read authority refused");return failure(scope,InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE);}
    private static boolean id(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static boolean text(String value,int maximum){return value!=null&&!value.isBlank()&&value.length()<=maximum&&value.equals(value.strip())&&!value.codePoints().anyMatch(Character::isISOControl);}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original initial loan contract authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Original Contract link observation unavailable");}
    @Override public String toString(){return "TokenExchangedInitialLoanContractOwnerPortImpl[redacted]";}
}
