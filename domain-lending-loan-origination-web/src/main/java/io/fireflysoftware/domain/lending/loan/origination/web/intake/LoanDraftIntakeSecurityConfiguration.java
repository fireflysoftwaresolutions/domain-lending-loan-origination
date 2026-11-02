/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.intake;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.intake.enabled",havingValue="true")
public class LoanDraftIntakeSecurityConfiguration {
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.fireflyframework.security.spi.SecurityContextPort.class)
    public org.fireflyframework.security.spi.SecurityContextPort lendingIntakeSecurityContext(){
        return new org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter();
    }
    @Bean @Order(-50) public org.springframework.web.server.WebFilter draftIntakeVerifiedSubject(
            ObjectProvider<org.springframework.security.oauth2.jwt.ReactiveJwtDecoder> decoders,
            @Qualifier("fireflyJwtAuthenticationConverter") ObjectProvider<org.springframework.core.convert.converter.Converter<org.springframework.security.oauth2.jwt.Jwt,
                reactor.core.publisher.Mono<org.springframework.security.authentication.AbstractAuthenticationToken>>> projections,
            ObjectProvider<org.fireflyframework.security.core.context.PrincipalFactory> factories,LoanIntakeExchangeSettings settings,Environment env){
        var existingDecoders=decoders.stream().limit(2).toList();if(existingDecoders.size()>1)throw new IllegalStateException("One trusted intake decoder required");
        org.springframework.security.oauth2.jwt.ReactiveJwtDecoder decoder;
        if(existingDecoders.isEmpty()){
            String value=env.getRequiredProperty("lending.intake.jwk-set-uri");var uri=java.net.URI.create(value);
            boolean sandbox=Boolean.parseBoolean(env.getProperty("lending.intake.owned-loopback-sandbox","false"));
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !("https".equals(uri.getScheme()) || sandbox && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort()>0))
                throw new IllegalArgumentException("Explicit trusted intake JWK endpoint required");
            var owned=org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(value).build();
            owned.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(settings.issuer()));decoder=owned;
        }else decoder=existingDecoders.getFirst();
        var existingProjections=projections.stream().limit(2).toList();if(existingProjections.size()>1)throw new IllegalStateException("One configured intake projection required");
        org.springframework.core.convert.converter.Converter<org.springframework.security.oauth2.jwt.Jwt,reactor.core.publisher.Mono<org.springframework.security.authentication.AbstractAuthenticationToken>> projection;
        if(existingProjections.isEmpty()){
            var configured=factories.stream().limit(2).toList();if(configured.size()>1)throw new IllegalStateException("One configured principal factory required");
            var factory=configured.isEmpty()?new org.fireflyframework.security.core.context.PrincipalFactory(
                new org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper(new org.fireflyframework.security.core.authority.AuthorityMappingProperties(
                    java.util.Arrays.asList(env.getRequiredProperty("lending.intake.role-claim-paths").split(",",-1)),
                    java.util.Arrays.asList(env.getRequiredProperty("lending.intake.scope-claim-paths").split(",",-1)),
                    env.getRequiredProperty("lending.intake.authority-prefix")))):configured.getFirst();
            projection=new io.fireflysoftware.domain.lending.loan.origination.web.intake.impl.LendingJwtPrincipalConverterImpl(factory,
                new org.fireflyframework.security.core.tenant.ConfigurableTenantResolver(java.util.List.of("tenant_id")));
        }else projection=existingProjections.getFirst();
        return new io.fireflysoftware.domain.lending.loan.origination.web.intake.impl.VerifiedLendingJwtWebFilterImpl(decoder,projection,
            settings.issuer(),settings.platformTenant(),"/api/v1/loan-application-intakes");
    }
}
