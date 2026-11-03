/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.controller;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.*;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl.AuthenticatedManagedEvidenceAuthorityImpl;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Entrypoint/error metadata tests. Actual current auth, SDK owners and persistence remain separate. */
class ManagedLoanEvidenceControllerContractTest {
    @Test void providerSetupFailureIsTypedBeforeAnyOperation(){
        ObjectProvider<AuthenticatedManagedEvidenceAuthorityImpl> authorities=mock(ObjectProvider.class);ObjectProvider<ManagedEvidenceService> services=mock(ObjectProvider.class);when(authorities.getIfUnique()).thenThrow(new IllegalStateException("Controlled provider setup"));var controller=new ManagedLoanEvidenceController(authorities,services);UUID action=UUID.randomUUID();
        StepVerifier.create(controller.recover(UUID.randomUUID(),action)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ManagedEvidenceFailure.class);var failure=(ManagedEvidenceFailure)error;assertThat(failure.actionId()).isEqualTo(action);assertThat(failure.outcome()).isEqualTo(ManagedEvidenceFailure.Outcome.OBSERVATION_UNAVAILABLE);}).verify();verifyNoInteractions(services);
    }
    @Test void bothMutatingOperationsOptOutOfCachedIdempotencyAndPublishTypedCreatedReplayAndFailures()throws Exception{
        for(String name:List.of("upload","recover")){var method=Arrays.stream(ManagedLoanEvidenceController.class.getDeclaredMethods()).filter(value->value.getName().equals(name)).findFirst().orElseThrow();assertThat(method.getAnnotation(PostMapping.class)).isNotNull();assertThat(method.getAnnotation(DisableIdempotency.class)).isNotNull();var responses=method.getAnnotation(ApiResponses.class);assertThat(Arrays.stream(responses.value()).map(value->value.responseCode()).toList()).contains("200","201","409","503");for(var response:responses.value())if(Set.of("200","201","409","503").contains(response.responseCode()))assertThat(response.content()[0].schema().implementation()).isEqualTo(Set.of("200","201").contains(response.responseCode())?ManagedEvidenceReceipt.class:ManagedEvidenceError.class);}
    }
}
