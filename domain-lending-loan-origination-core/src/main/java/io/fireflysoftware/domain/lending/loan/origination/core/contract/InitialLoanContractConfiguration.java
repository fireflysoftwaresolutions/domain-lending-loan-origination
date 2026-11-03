/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.impl.*;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import java.net.URI;
import java.time.*;
import java.util.UUID;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.initial-primary-contract.enabled",havingValue="true")
public class InitialLoanContractConfiguration {
    private static final String P="lending.initial-primary-contract.";
    @Bean public InitialLoanContractAuthoritySettings initialLoanContractAuthoritySettings(Environment e){return new InitialLoanContractAuthoritySettings(e.getRequiredProperty(P+"issuer"),e.getRequiredProperty(P+"incoming-audience"),e.getRequiredProperty(P+"incoming-client-id"),e.getRequiredProperty(P+"platform-tenant"),UUID.fromString(e.getRequiredProperty(P+"loan-resource-tenant-id")),UUID.fromString(e.getRequiredProperty(P+"contract-resource-tenant-id")),Instant.parse(e.getRequiredProperty(P+"namespace-not-after")));}
    @Bean public InitialLoanContractAuthority initialLoanContractAuthority(InitialLoanContractAuthoritySettings s,ObjectProvider<PolicyEvaluationPort> policies){var p=policies.getIfUnique();if(p==null)throw new IllegalStateException("Exactly one current initial loan contract policy provider required");return new AuthenticatedInitialLoanContractAuthorityImpl(s,p,Clock.systemUTC(),System::nanoTime);}
    @Bean public InitialLoanContractService initialLoanContractService(InitialLoanContractAuthority a,InitialLoanContractLoanPort l,InitialLoanContractOwnerPort c){return new InitialLoanContractOrchestratorImpl(a,l,c,Clock.systemUTC(),System::nanoTime);}
    @Bean public InitialLoanContractExchangeSettings initialLoanContractExchangeSettings(Environment e,InitialLoanContractAuthoritySettings a){return new InitialLoanContractExchangeSettings(a.issuer(),a.audience(),a.incomingClientId(),a.platformTenant(),e.getRequiredProperty(P+"contract-audience"),e.getRequiredProperty(P+"contract-client-id"),a.loanResourceTenantId(),a.contractResourceTenantId(),UUID.fromString(e.getRequiredProperty(P+"role-in-contract-id")),URI.create(e.getRequiredProperty(P+"contract-endpoint")),Boolean.parseBoolean(e.getProperty(P+"owned-loopback-sandbox","false")));}
    @Bean public InitialLoanContractOwnerPort initialLoanContractOwnerPort(Environment e,InitialLoanContractExchangeSettings s,InitialLoanContractAuthority a){return new TokenExchangedInitialLoanContractOwnerPortImpl(s,registration(e,s.contractClientId(),"initialContractLink",s.ownedLoopbackSandbox(),"contract-client-secret","contracts:loan:initial-primary-link:write"),registration(e,s.contractClientId(),"initialContractRead",s.ownedLoopbackSandbox(),"contract-client-secret","contracts:loan:initial-primary-link:read"),decoder(e,s.issuer(),s.ownedLoopbackSandbox()),Clock.systemUTC(),System::nanoTime,a);}
    @Bean public InitialLoanContractLoanExchangeSettings initialLoanContractLoanExchangeSettings(Environment e,InitialLoanContractAuthoritySettings a){return new InitialLoanContractLoanExchangeSettings(a.issuer(),a.audience(),a.incomingClientId(),a.platformTenant(),e.getRequiredProperty(P+"loan-audience"),e.getRequiredProperty(P+"loan-client-id"),a.loanResourceTenantId(),a.contractResourceTenantId(),URI.create(e.getRequiredProperty(P+"loan-endpoint")),Boolean.parseBoolean(e.getProperty(P+"owned-loopback-sandbox","false")));}
    @Bean public InitialLoanContractLoanPort initialLoanContractLoanPort(Environment e,InitialLoanContractLoanExchangeSettings s,InitialLoanContractAuthority a){return new TokenExchangedInitialLoanContractLoanPortImpl(s,registration(e,s.loanClientId(),"initialContractLoanBasis",s.ownedLoopbackSandbox(),"loan-client-secret","lending:applications:initial-primary-contract:read"),decoder(e,s.issuer(),s.ownedLoopbackSandbox()),Clock.systemUTC(),System::nanoTime,a);}
    private static ReactiveJwtDecoder decoder(Environment e,String issuer,boolean sandbox){String jwks=LoanIntakeExchangeSettings.endpoint(URI.create(e.getRequiredProperty(P+"target-jwk-set-uri")),sandbox).toASCIIString();var d=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();d.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));return d;}
    private static ClientRegistration registration(Environment e,String client,String id,boolean sandbox,String secretProperty,String scope){String token=LoanIntakeExchangeSettings.endpoint(URI.create(e.getRequiredProperty(P+"token-endpoint")),sandbox).toASCIIString();return ClientRegistration.withRegistrationId(id).clientId(client).clientSecret(e.getRequiredProperty(P+secretProperty)).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token).scope(scope).build();}
}
