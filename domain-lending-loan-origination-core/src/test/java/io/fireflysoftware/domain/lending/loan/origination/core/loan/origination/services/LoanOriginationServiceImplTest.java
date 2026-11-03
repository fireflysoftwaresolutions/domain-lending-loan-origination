/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.services;

import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.commands.RegisterApplicationPartyCommand;
import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.commands.RegisterLoanApplicationCommand;
import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.commands.SubmitApplicationCommand;
import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.services.impl.LoanOriginationServiceImpl;
import io.fireflysoftware.domain.lending.loan.origination.core.shared.CustomerContractLink;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanOriginationServiceImplTest {

    @Mock
    private SagaEngine engine;

    @Mock
    private QueryBus queryBus;

    @Mock
    private CommandBus commandBus;

    private LoanOriginationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoanOriginationServiceImpl(engine, queryBus, commandBus);
    }

    /**
     * QA Issue 1 / Bug 2: a minimal SubmitApplicationCommand with all collection fields left null
     * (parties, documents, offers, statusHistories) must NOT trigger an NPE when expanding into
     * saga step inputs. The service must defensively default the lists before calling
     * {@code ExpandEach.of(...)}.
     */
    @Test
    void submitApplication_doesNotThrowNpe_whenCollectionsAreNull() {
        var application = new RegisterLoanApplicationCommand();
        // loanApplicationId on the underlying core DTO is generated server-side and read-only;
        // leaving it unset is exactly what the experience tier does for a new application.

        var command = new SubmitApplicationCommand();
        command.setApplication(application);
        // parties, documents, offers, statusHistories left null on purpose

        SagaResult sagaResult = mock(SagaResult.class);
        when(sagaResult.isSuccess()).thenReturn(true);
        when(engine.execute(eq("RegisterApplicationSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        StepVerifier.create(service.submitApplication(command))
                .expectNextMatches(SagaResult::isSuccess)
                .verifyComplete();

        ArgumentCaptor<StepInputs> captor = ArgumentCaptor.forClass(StepInputs.class);
        verify(engine).execute(eq("RegisterApplicationSaga"), captor.capture());
        // Reaching this point without an NPE during builder execution proves the fix.
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void submitApplication_refusesUnmanagedContractBindingBeforeTheSagaWritesTheLoan() {
        UUID partyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        var party = new RegisterApplicationPartyCommand();
        party.setPartyId(partyId);
        var command = new SubmitApplicationCommand();
        command.setApplication(new RegisterLoanApplicationCommand());
        command.setParties(List.of(party));

        CustomerContractLink link = (id, existingContract, productId, currency, roleId) -> {
            assertThat(id).isEqualTo(partyId);
            return Mono.just(contractId);
        };
        var linked = new LoanOriginationServiceImpl(engine, queryBus, commandBus, link);
        StepVerifier.create(linked.submitApplication(command))
                .expectErrorMatches(error -> error instanceof org.springframework.web.server.ResponseStatusException status
                        && status.getStatusCode().value() == 422)
                .verify();
        assertThat(party.getContractId()).isNull();
        assertThat(party.isContractLinked()).isFalse();
        verify(engine, never()).execute(anyString(), any(StepInputs.class));
    }

    @Test
    void submitApplication_doesNotWriteTheLoanWhenThePartyIsMissing() {
        var party = new RegisterApplicationPartyCommand();
        var command = new SubmitApplicationCommand();
        command.setApplication(new RegisterLoanApplicationCommand());
        command.setParties(List.of(party));

        CustomerContractLink link = (id, existingContract, productId, currency, roleId) ->
                Mono.error(new IllegalArgumentException("Party is required"));
        var linked = new LoanOriginationServiceImpl(engine, queryBus, commandBus, link);

        StepVerifier.create(linked.submitApplication(command))
                .expectErrorMatches(error -> error instanceof org.springframework.web.server.ResponseStatusException status
                        && status.getStatusCode().value() == 422)
                .verify();

        verify(engine, never()).execute(anyString(), any(StepInputs.class));
    }
}
