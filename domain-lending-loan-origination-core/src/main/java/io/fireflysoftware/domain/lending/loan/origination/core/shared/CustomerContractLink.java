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

import reactor.core.publisher.Mono;

import java.util.UUID;

/** Legacy source compatibility only; the missing application and original action prevent a governed link. */
@Deprecated
@FunctionalInterface
public interface CustomerContractLink {

    /** Legacy callers are refused; UUID input/output is not a verified original-action owner receipt. */
    Mono<UUID> bind(UUID partyId, UUID contractId, UUID productId, String currency, UUID roleInContractId);
}
