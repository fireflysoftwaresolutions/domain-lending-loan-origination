/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;

import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import java.time.*;
import java.util.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Private-context and identity contract only; constructed JWT is not native authentication or an owner operation. */
class ManagedEvidenceAuthorityContextTest {
    final Clock clock=Clock.systemUTC();final UUID app=UUID.randomUUID(),action=UUID.randomUUID(),party=UUID.randomUUID(),loanTenant=UUID.randomUUID(),documentTenant=UUID.randomUUID();
    final ManagedEvidenceAuthoritySettings settings=new ManagedEvidenceAuthoritySettings("https://issuer.invalid","lending-domain","experience-lending","platform",loanTenant,documentTenant,clock.instant().plusSeconds(60));
    final AuthenticatedManagedEvidenceAuthorityImpl authority=new AuthenticatedManagedEvidenceAuthorityImpl(settings,(principal,operation,resource,facts)->Mono.just(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("evidence-policy-1","decision")))),clock,System::nanoTime);
    @Test void signedFullContextDerivesPartyAndBothOwnerNamespaces(){
        StepVerifier.create(authority.invoke(app,action,()->authority.requireCurrent(app,action).flatMap(scope->{
            assertThat(scope.partyId()).isEqualTo(party);assertThat(scope.loanResourceTenantId()).isEqualTo(loanTenant);assertThat(scope.documentResourceTenantId()).isEqualTo(documentTenant);return authority.revalidate(scope).then(authority.requireTransportContext(scope)).thenReturn(receipt());
        })).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication()))).expectNextCount(1).verifyComplete();
    }
    @Test void equalValuedCallerMintedScopeCannotConferAuthority(){
        StepVerifier.create(authority.invoke(app,action,()->authority.requireCurrent(app,action).flatMap(scope->authority.requireTransportContext(new ManagedEvidenceAuthority.Scope(scope.actionId(),scope.applicationId(),scope.partyId(),scope.loanResourceTenantId(),scope.documentResourceTenantId(),scope.issuer(),scope.subject(),scope.platformTenant(),scope.observedAt(),scope.observedNanos(),scope.notAfter())).thenReturn(receipt())))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication()))).expectError().verify();
    }
    @Test void equalClaimsReplacementAuthenticationCannotReuseScope(){
        var original=authentication();var replacement=new JwtAuthenticationToken(original.getToken(),original.getPrincipal(),original.getAuthorities());
        StepVerifier.create(authority.invoke(app,action,()->authority.requireCurrent(app,action).flatMap(scope->authority.revalidate(scope).contextWrite(ReactiveSecurityContextHolder.withAuthentication(replacement)).thenReturn(receipt())))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).expectError().verify();
    }
    @Test void ambientAuthenticationAloneCannotMintScope(){StepVerifier.create(authority.requireCurrent(app,action).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication()))).expectError().verify();}
    JwtAuthenticationToken authentication(){
        var now=clock.instant();var jwt=Jwt.withTokenValue("constructed-not-signature-proof").header("alg","RS256").issuer(settings.issuer()).subject("opaque:human").audience(List.of(settings.audience())).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(30))
            .claim("client_id",settings.incomingClientId()).claim("principal_kind","HUMAN").claim("tenant_id",settings.platformTenant()).claim("party_id",party.toString()).claim("scope",AuthenticatedManagedEvidenceAuthorityImpl.ACTION).build();
        var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));
        return new JwtAuthenticationToken(jwt,factory.fromClaims(jwt.getSubject(),jwt.getIssuer().toString(),jwt.getClaims()).toBuilder().tenantId(settings.platformTenant()).build(),List.of());
    }
    ManagedEvidenceReceipt receipt(){return new ManagedEvidenceReceipt(action,app,party,UUID.randomUUID(),UUID.randomUUID(),"proof.pdf","IDENTITY","application/pdf",3,"a".repeat(64),"b".repeat(64),"c".repeat(64),clock.instant(),clock.instant(),"ATTACHED","PENDING",false);}
}
