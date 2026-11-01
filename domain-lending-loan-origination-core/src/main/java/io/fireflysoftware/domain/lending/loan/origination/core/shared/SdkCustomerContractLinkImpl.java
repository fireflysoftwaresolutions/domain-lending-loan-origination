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

package io.fireflysoftware.domain.lending.loan.origination.core.shared;

import io.fireflysoftware.core.contract.sdk.api.ContractPartiesApi;
import io.fireflysoftware.core.contract.sdk.api.ContractsApi;
import io.fireflysoftware.core.contract.sdk.invoker.ApiClient;
import io.fireflysoftware.core.contract.sdk.model.ContractDTO;
import io.fireflysoftware.core.contract.sdk.model.ContractPartyDTO;
import io.fireflysoftware.core.customer.sdk.api.PartiesApi;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Validates the party with {@code core-common-customer-mgmt-sdk} and links the contract
 * with {@code core-common-contract-mgmt-sdk}. The loan is written only after this returns.
 */
public final class SdkCustomerContractLinkImpl implements CustomerContractLink {

    /**
     * {@code contract_party.role_in_contract_id} is required and has no foreign key.
     * Callers that already name a role keep that id.
     */
    static final UUID UNSPECIFIED_CONTRACT_ROLE = UUID.fromString("10000000-0000-4000-8000-000000000301");

    private final PartiesApi parties;
    private final ContractsApi contracts;
    private final ContractPartiesApi contractParties;

    public SdkCustomerContractLinkImpl(String customerBaseUrl, String contractBaseUrl) {
        io.fireflysoftware.core.customer.sdk.invoker.ApiClient customers = new io.fireflysoftware.core.customer.sdk.invoker.ApiClient();
        customers.setBasePath(strip(customerBaseUrl));
        ApiClient agreements = new ApiClient();
        agreements.setBasePath(strip(contractBaseUrl));
        this(new PartiesApi(customers), new ContractsApi(agreements), new ContractPartiesApi(agreements));
    }

    SdkCustomerContractLinkImpl(PartiesApi parties, ContractsApi contracts, ContractPartiesApi contractParties) {
        this.parties = parties;
        this.contracts = contracts;
        this.contractParties = contractParties;
    }

    @Override
    public Mono<UUID> bind(UUID partyId, UUID contractId, UUID productId, String currency, UUID roleInContractId) {
        if (partyId == null) {
            return Mono.error(new IllegalArgumentException("Party is required"));
        }
        String key = UUID.randomUUID().toString();
        UUID role = roleInContractId != null ? roleInContractId : UNSPECIFIED_CONTRACT_ROLE;
        return parties.getPartyById(partyId, key)
                .switchIfEmpty(Mono.error(new IllegalStateException("Party " + partyId + " was not found")))
                .flatMap(party -> party.getPartyId() == null
                        ? Mono.error(new IllegalStateException("Party " + partyId + " was not found"))
                        : resolveContract(contractId, productId, currency, key))
                .flatMap(id -> attach(id, partyId, role, key).thenReturn(id));
    }

    private Mono<UUID> resolveContract(UUID contractId, UUID productId, String currency, String key) {
        if (contractId != null) {
            return contracts.getContractById(contractId, key)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Contract " + contractId + " was not found")))
                    .map(contract -> {
                        if (contract.getContractId() == null) {
                            throw new IllegalStateException("Contract " + contractId + " was not found");
                        }
                        return contract.getContractId();
                    });
        }
        ContractDTO draft = new ContractDTO()
                .contractStatus(ContractDTO.ContractStatusEnum.DRAFT)
                .productId(productId)
                .currencyCode(currency);
        return contracts.createContract(draft, key)
                .switchIfEmpty(Mono.error(new IllegalStateException("Contract was not created")))
                .map(created -> {
                    if (created.getContractId() == null) {
                        throw new IllegalStateException("Contract was not created");
                    }
                    return created.getContractId();
                });
    }

    private Mono<ContractPartyDTO> attach(UUID contractId, UUID partyId, UUID roleId, String key) {
        ContractPartyDTO row = new ContractPartyDTO()
                .contractId(contractId)
                .partyId(partyId)
                .roleInContractId(roleId);
        return contractParties.createContractParty(contractId, row, key);
    }

    private static String strip(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8080";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
