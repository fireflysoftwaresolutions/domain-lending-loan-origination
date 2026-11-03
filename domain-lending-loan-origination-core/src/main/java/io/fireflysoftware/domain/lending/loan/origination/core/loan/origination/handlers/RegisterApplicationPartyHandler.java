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


package io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.handlers;

import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import io.fireflysoftware.core.lending.origination.sdk.api.ApplicationPartyApi;
import io.fireflysoftware.domain.lending.loan.origination.core.loan.origination.commands.RegisterApplicationPartyCommand;
import io.fireflysoftware.domain.lending.loan.origination.core.shared.CustomerContractLink;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@CommandHandlerComponent
public class RegisterApplicationPartyHandler extends CommandHandler<RegisterApplicationPartyCommand, UUID> {

    private final ApplicationPartyApi applicationPartyApi;
    private final CustomerContractLink customerContract;

    public RegisterApplicationPartyHandler(ApplicationPartyApi applicationPartyApi, CustomerContractLink customerContract) {
        this.applicationPartyApi = applicationPartyApi;
        this.customerContract = customerContract;
    }

    @Override
    protected Mono<UUID> doHandle(RegisterApplicationPartyCommand cmd) {
        return Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "Caller contractId/contractLinked cannot prove an owner link; use the managed original-action contract operation"));
    }
}