/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.intake;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.CoreDraftIntakePort;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.impl.TokenExchangedCoreDraftIntakePortImpl;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="lending.intake.enabled",havingValue="true")
public class LoanDraftIntakeConfiguration {
    @Bean public LoanIntakeExchangeSettings lendingIntakeExchangeSettings(Environment env){
        String p="lending.intake.";boolean sandbox=Boolean.parseBoolean(env.getProperty(p+"owned-loopback-sandbox","false"));
        return new LoanIntakeExchangeSettings(env.getRequiredProperty(p+"issuer"),env.getRequiredProperty(p+"incoming-audience"),
            env.getRequiredProperty(p+"incoming-client-id"),env.getRequiredProperty(p+"platform-tenant"),env.getRequiredProperty(p+"core-audience"),
            env.getRequiredProperty(p+"core-client-id"),URI.create(env.getRequiredProperty(p+"core-endpoint")),sandbox);
    }
    @Bean public CoreDraftIntakePort coreDraftIntakePort(Environment env,LoanIntakeExchangeSettings settings){
        String p="lending.intake.";boolean sandbox=settings.ownedLoopbackSandbox();
        String token=LoanIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(p+"token-endpoint")),sandbox).toASCIIString();
        String jwks=LoanIntakeExchangeSettings.endpoint(URI.create(env.getRequiredProperty(p+"target-jwk-set-uri")),sandbox).toASCIIString();
        var registration=ClientRegistration.withRegistrationId("lendingCoreIntake").clientId(settings.coreClientId())
            .clientSecret(env.getRequiredProperty(p+"client-secret")).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(token)
            .scope("lending:applications:draft:create").build();
        var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
        return new TokenExchangedCoreDraftIntakePortImpl(settings,registration,decoder,Clock.systemUTC());
    }
}
