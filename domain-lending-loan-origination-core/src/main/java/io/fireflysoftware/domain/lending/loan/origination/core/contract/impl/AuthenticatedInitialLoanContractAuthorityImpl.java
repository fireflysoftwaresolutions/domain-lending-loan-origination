/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;

import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Retains full native authentication and private execution identity; a Scope value is never an admission grant. */
public final class AuthenticatedInitialLoanContractAuthorityImpl implements InitialLoanContractAuthority {
    public static final String LINK="lending:applications:initial-primary-contract:link",READ="lending:applications:initial-primary-contract:read";
    static String action(Purpose purpose){return InitialLoanContractAuthority.action(purpose);}
    private final InitialLoanContractAuthoritySettings settings;private final PolicyEvaluationPort policies;private final Clock clock;private final LongSupplier nanos;
    private final Object executionKey=new Object();
    public AuthenticatedInitialLoanContractAuthorityImpl(InitialLoanContractAuthoritySettings settings,PolicyEvaluationPort policies,Clock clock,LongSupplier nanos){this.settings=Objects.requireNonNull(settings);this.policies=Objects.requireNonNull(policies);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);}
    public <T> Mono<T> invoke(UUID application,UUID action,Purpose purpose,Supplier<Mono<T>> operation){return Mono.deferContextual(context->{
        if(context.hasKey(executionKey)||!valid(application)||!valid(action)||purpose==null)return Mono.error(denied());
        Instant observed=clock.instant();long before=nanos.getAsLong();
        return authentication().timeout(Duration.ofSeconds(5)).flatMap(authentication->{
            var principal=principal(authentication,purpose);Instant until=Collections.min(List.of(observed.plusSeconds(5),authentication.getToken().getExpiresAt(),settings.notAfter()));
            var execution=new ExecutionImpl(application,action,purpose,authentication,principal,UUID.fromString((String)authentication.getToken().getClaims().get("party_id")),observed,before,until);
            return Mono.using(()->execution,held->Mono.defer(operation).switchIfEmpty(Mono.error(unavailable())).contextWrite(current->current.put(executionKey,held)),held->held.active.set(false),true);
        });
    });}
    @Override public Mono<Scope> requireCurrent(UUID application,UUID action,Purpose purpose){return Mono.deferContextual(context->{
        var execution=current(context);if(!execution.application.equals(application)||!execution.action.equals(action)||execution.purpose!=purpose||execution.scope.get()!=null)return Mono.error(denied());
        return evaluate(execution,"authorize").map(evaluation->{
            String revision=evaluation.provenance().orElseThrow().policyRevision();if(!revision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))throw unavailable();
            var scope=new Scope(action,application,execution.party,settings.loanResourceTenantId(),settings.contractResourceTenantId(),execution.principal.issuer(),execution.principal.subject(),execution.principal.tenantId(),execution.purpose,execution.observed,execution.observedNanos,execution.until);
            if(!execution.scope.compareAndSet(null,scope)||!execution.policyRevision.compareAndSet(null,revision))throw denied();return scope;
        });
    });}
    public Mono<Void> requireTransportContext(Scope scope){return Mono.deferContextual(context->{
        var execution=current(context);if(execution.scope.get()!=scope||execution.policyRevision.get()==null)return Mono.error(denied());return same(execution).then();
    });}
    @Override public Mono<Void> revalidate(Scope scope){return Mono.deferContextual(context->{
        var execution=current(context);if(execution.scope.get()!=scope)return Mono.error(denied());
        return evaluate(execution,"current").flatMap(evaluation->Objects.equals(execution.policyRevision.get(),evaluation.provenance().orElseThrow().policyRevision())?Mono.empty():Mono.error(denied()));
    });}
    private Mono<PolicyEvaluation> evaluate(ExecutionImpl execution,String stage){return same(execution).flatMap(principal->{
        Map<String,Object> facts=Map.of("profile","initial-primary-loan-contract-1","stage",stage,"applicationId",execution.application,"actionId",execution.action,"partyId",execution.party,
            "loanResourceTenantId",settings.loanResourceTenantId(),"contractResourceTenantId",settings.contractResourceTenantId());
        return policies.evaluate(principal,action(execution.purpose),"urn:firefly:initial-loan-contract:application:"+execution.application+":party:"+execution.party+":action:"+execution.action,facts)
            .switchIfEmpty(Mono.error(unavailable())).flatMap(evaluation->{
                if(!evaluation.decision().granted())return Mono.error(denied());
                if(!evaluation.decision().obligations().isEmpty()||evaluation.provenance().isEmpty())return Mono.error(unavailable());
                return same(execution).thenReturn(evaluation);
            });
    }).timeout(remaining(execution));}
    private Mono<SecurityPrincipal> same(ExecutionImpl execution){return authentication().filter(current->current==execution.authentication).switchIfEmpty(Mono.error(denied())).map(current->{
        var value=principal(current,execution.purpose);if(!value.equals(execution.principal))throw denied();remaining(execution);return value;
    });}
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(value->value instanceof JwtAuthenticationToken&&value.isAuthenticated()).cast(JwtAuthenticationToken.class).switchIfEmpty(Mono.error(denied()));}
    private SecurityPrincipal principal(JwtAuthenticationToken authentication,Purpose purpose){
        Jwt jwt=authentication.getToken();Instant now=clock.instant();settings.requireCurrent(now);Object rawParty=jwt.getClaims().get("party_id"),rawScope=jwt.getClaims().get("scope");
        if(jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||jwt.getSubject()==null||jwt.getSubject().isBlank()
            ||!List.of(settings.audience()).equals(jwt.getAudience())||!settings.incomingClientId().equals(jwt.getClaims().get("client_id"))||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))
            ||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())
            ||!(rawParty instanceof String signedParty)||!(rawScope instanceof String scopes)||!Set.copyOf(Arrays.asList(scopes.split(" +"))).contains(action(purpose)))throw denied();
        UUID party;try{party=UUID.fromString((String)rawParty);}catch(IllegalArgumentException malformed){throw denied();}
        if(!valid(party)||!party.toString().equals(rawParty))throw denied();
        if(!(authentication.getPrincipal() instanceof SecurityPrincipal projected)||!settings.issuer().equals(projected.issuer())||!jwt.getSubject().equals(projected.subject())||!settings.platformTenant().equals(projected.tenantId())
            ||!projected.claims().equals(jwt.getClaims())||!projected.hasScope(action(purpose)))throw denied();return projected;
    }
    private ExecutionImpl current(reactor.util.context.ContextView context){Object raw=context.getOrDefault(executionKey,null);if(!(raw instanceof ExecutionImpl execution)||!execution.active.get())throw denied();remaining(execution);return execution;}
    private Duration remaining(ExecutionImpl execution){if(!execution.active.get())throw denied();return window(execution);}
    private Duration window(ExecutionImpl execution){long elapsed=nanos.getAsLong()-execution.observedNanos;var wall=Duration.between(clock.instant(),execution.until);var monotonic=Duration.between(execution.observed,execution.until).minusNanos(elapsed);
        if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;}
    private static final class ExecutionImpl {
        final UUID application,action,party;final Purpose purpose;final JwtAuthenticationToken authentication;final SecurityPrincipal principal;final Instant observed,until;final long observedNanos;
        final AtomicBoolean active=new AtomicBoolean(true);final AtomicReference<Scope> scope=new AtomicReference<>();final AtomicReference<String> policyRevision=new AtomicReference<>();
        ExecutionImpl(UUID application,UUID action,Purpose purpose,JwtAuthenticationToken authentication,SecurityPrincipal principal,UUID party,Instant observed,long observedNanos,Instant until){this.application=application;this.action=action;this.purpose=purpose;this.authentication=authentication;this.principal=principal;this.party=party;this.observed=observed;this.observedNanos=observedNanos;this.until=until;}
    }
    private static boolean valid(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current initial loan contract application and party authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current initial loan contract authority unavailable");}
}
