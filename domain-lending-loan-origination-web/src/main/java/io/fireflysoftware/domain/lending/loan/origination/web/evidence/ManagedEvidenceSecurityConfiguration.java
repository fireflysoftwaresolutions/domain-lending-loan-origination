/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.evidence;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.ManagedEvidenceAuthoritySettings;
import io.fireflysoftware.domain.lending.loan.origination.web.intake.impl.VerifiedLendingJwtWebFilterImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.evidence.enabled",havingValue="true")
public class ManagedEvidenceSecurityConfiguration {
    @Bean @Order(-49) public WebFilter managedEvidenceVerifiedSubject(ReactiveJwtDecoder decoder,
            @Qualifier("fireflyJwtAuthenticationConverter") Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,ManagedEvidenceAuthoritySettings settings){
        return new VerifiedLendingJwtWebFilterImpl(decoder,projection,settings.issuer(),settings.platformTenant(),"/api/v1/managed-loan-evidence");
    }
    @Bean @Order(-48) public WebFilter managedEvidenceEnvelope(){return new io.fireflysoftware.domain.lending.loan.origination.web.evidence.impl.ManagedEvidenceEnvelopeWebFilterImpl();}
}
