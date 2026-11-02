/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl.*;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import java.net.URI;
import java.time.*;
import java.util.UUID;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.evidence.enabled",havingValue="true")
public class ManagedEvidenceConfiguration {
    private static final String PREFIX="lending.evidence.";
    @Bean public ManagedEvidenceAuthoritySettings managedEvidenceAuthoritySettings(Environment env){return new ManagedEvidenceAuthoritySettings(
        env.getRequiredProperty(PREFIX+"issuer"),env.getRequiredProperty(PREFIX+"incoming-audience"),env.getRequiredProperty(PREFIX+"incoming-client-id"),env.getRequiredProperty(PREFIX+"platform-tenant"),
        UUID.fromString(env.getRequiredProperty(PREFIX+"loan-resource-tenant-id")),UUID.fromString(env.getRequiredProperty(PREFIX+"document-resource-tenant-id")),Instant.parse(env.getRequiredProperty(PREFIX+"namespace-not-after")));}
    @Bean public AuthenticatedManagedEvidenceAuthorityImpl managedEvidenceAuthority(ManagedEvidenceAuthoritySettings settings,PolicyEvaluationPort policies){return new AuthenticatedManagedEvidenceAuthorityImpl(settings,policies,Clock.systemUTC(),System::nanoTime);}
    @Bean public ManagedEvidenceService managedEvidenceService(AuthenticatedManagedEvidenceAuthorityImpl authority,ManagedDocumentEvidencePort documents,ManagedLoanEvidencePort loans){return new ManagedEvidenceOrchestratorImpl(authority,documents,loans,Clock.systemUTC(),System::nanoTime);}
    @Bean public ManagedDocumentExchangeSettings managedDocumentExchangeSettings(Environment env,ManagedEvidenceAuthoritySettings authority){return new ManagedDocumentExchangeSettings(authority.issuer(),authority.audience(),authority.incomingClientId(),authority.platformTenant(),
        env.getRequiredProperty(PREFIX+"document-audience"),env.getRequiredProperty(PREFIX+"document-client-id"),authority.documentResourceTenantId(),URI.create(env.getRequiredProperty(PREFIX+"document-endpoint")),Boolean.parseBoolean(env.getProperty(PREFIX+"owned-loopback-sandbox","false")));}
    @Bean public ManagedDocumentEvidencePort managedDocumentEvidencePort(Environment env,ManagedDocumentExchangeSettings settings,AuthenticatedManagedEvidenceAuthorityImpl authority){
        boolean sandbox=settings.ownedLoopbackSandbox();String token=LoanIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(PREFIX+"token-endpoint")),sandbox).toASCIIString();
        String jwks=LoanIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(PREFIX+"target-jwk-set-uri")),sandbox).toASCIIString();
        var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
        return new TokenExchangedManagedDocumentEvidencePortImpl(settings,registration(env,settings,"managedDocumentEvidenceUpload",token,"document:evidence:upload"),registration(env,settings,"managedDocumentEvidenceRead",token,"document:evidence:read"),decoder,Clock.systemUTC(),System::nanoTime,authority);
    }
    @Bean public ManagedLoanExchangeSettings managedLoanExchangeSettings(Environment env,ManagedEvidenceAuthoritySettings authority){return new ManagedLoanExchangeSettings(authority.issuer(),authority.audience(),authority.incomingClientId(),authority.platformTenant(),
        env.getRequiredProperty(PREFIX+"loan-audience"),env.getRequiredProperty(PREFIX+"loan-client-id"),authority.loanResourceTenantId(),authority.documentResourceTenantId(),URI.create(env.getRequiredProperty(PREFIX+"loan-endpoint")),Boolean.parseBoolean(env.getProperty(PREFIX+"owned-loopback-sandbox","false")));}
    @Bean public ManagedLoanEvidencePort managedLoanEvidencePort(Environment env,ManagedLoanExchangeSettings settings,AuthenticatedManagedEvidenceAuthorityImpl authority){
        boolean sandbox=settings.ownedLoopbackSandbox();String token=LoanIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(PREFIX+"token-endpoint")),sandbox).toASCIIString();String jwks=LoanIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(PREFIX+"target-jwk-set-uri")),sandbox).toASCIIString();var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
        return new TokenExchangedManagedLoanEvidencePortImpl(settings,loanRegistration(env,settings,"managedLoanEvidenceAttach",token,"lending:applications:evidence:attach"),loanRegistration(env,settings,"managedLoanEvidenceRead",token,"lending:applications:evidence:read"),decoder,Clock.systemUTC(),System::nanoTime,authority);
    }
    private static ClientRegistration loanRegistration(Environment env,ManagedLoanExchangeSettings settings,String id,String token,String scope){return ClientRegistration.withRegistrationId(id).clientId(settings.loanClientId()).clientSecret(env.getRequiredProperty(PREFIX+"loan-client-secret")).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token).scope(scope).build();}
    private static ClientRegistration registration(Environment env,ManagedDocumentExchangeSettings settings,String id,String token,String scope){return ClientRegistration.withRegistrationId(id).clientId(settings.documentClientId()).clientSecret(env.getRequiredProperty(PREFIX+"document-client-secret"))
        .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token).scope(scope).build();}
}
