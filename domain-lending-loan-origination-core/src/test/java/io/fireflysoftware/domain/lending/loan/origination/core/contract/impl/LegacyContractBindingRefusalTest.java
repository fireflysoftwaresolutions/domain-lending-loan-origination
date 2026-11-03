/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.shared.*;
import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.handlers.RegisterApplicationPartyHandler;
import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.commands.RegisterApplicationPartyCommand;
import io.fireflysoftware.core.lending.origination.sdk.api.ApplicationPartyApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
class LegacyContractBindingRefusalTest {
    @Test void legacySandboxCannotInventOrEchoAnUnprovenContractIdentity(){StepVerifier.create(new SandboxCustomerContractLinkImpl().bind(UUID.randomUUID(),UUID.randomUUID(),null,null,null)).expectErrorMatches(LegacyContractBindingRefusalTest::refused).verify();}
    @Test void legacySdkSignatureCannotCreateAContractWithFreshRandomKeys(){StepVerifier.create(new SdkCustomerContractLinkImpl("https://customer.invalid","https://contract.invalid").bind(UUID.randomUUID(),null,null,null,null)).expectErrorMatches(LegacyContractBindingRefusalTest::refused).verify();}
    static final class ExposedPartyHandlerImpl extends RegisterApplicationPartyHandler {
        ExposedPartyHandlerImpl(ApplicationPartyApi api,CustomerContractLink legacy){super(api,legacy);}
        Mono<UUID> invoke(RegisterApplicationPartyCommand input){return doHandle(input);}
    }
    @Test void callerContractLinkedFlagCannotBypassOwnerVerificationOrWriteAParty(){var api=mock(ApplicationPartyApi.class);var legacy=mock(CustomerContractLink.class);var input=new RegisterApplicationPartyCommand();input.setLoanApplicationId(UUID.randomUUID());input.setPartyId(UUID.randomUUID());input.setContractId(UUID.randomUUID());input.setContractLinked(true);StepVerifier.create(new ExposedPartyHandlerImpl(api,legacy).invoke(input)).expectErrorMatches(LegacyContractBindingRefusalTest::refused).verify();verifyNoInteractions(api,legacy);}
    static boolean refused(Throwable error){return error instanceof ResponseStatusException status&&status.getStatusCode().value()==422;}
}
