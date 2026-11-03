/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.contract;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import io.fireflysoftware.domain.lending.loan.origination.web.controller.InitialLoanContractController;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
/** Controller-envelope fixtures only; owning and signed layered business execution is separate. */
class InitialLoanContractControllerTest {
    final UUID app=new UUID(0,1),action=new UUID(0,2);
    @SuppressWarnings("unchecked") @Test void unavailableProviderCannotBecomeASuccessOrUnknownDispatchedWrite(){ObjectProvider<InitialLoanContractAuthority> a=mock(ObjectProvider.class);ObjectProvider<InitialLoanContractService> s=mock(ObjectProvider.class);when(a.getIfUnique()).thenThrow(new IllegalStateException("controlled provider failure"));StepVerifier.create(new InitialLoanContractController(a,s).link(app,action,new InitialLoanContractRequest(action,null))).expectErrorMatches(e->e instanceof InitialLoanContractFailure f&&f.outcome()==InitialLoanContractFailure.Outcome.REFUSED&&action.equals(f.actionId())).verify();verifyNoInteractions(s);}
    @SuppressWarnings("unchecked") @Test void failedReadProviderIsTypedObservationUnavailableWithoutMutation(){ObjectProvider<InitialLoanContractAuthority> a=mock(ObjectProvider.class);ObjectProvider<InitialLoanContractService> s=mock(ObjectProvider.class);StepVerifier.create(new InitialLoanContractController(a,s).observe(app,action)).expectErrorMatches(e->e instanceof InitialLoanContractFailure f&&f.outcome()==InitialLoanContractFailure.Outcome.OBSERVATION_UNAVAILABLE&&action.equals(f.actionId())).verify();}
    @SuppressWarnings("unchecked") @Test void pathActionMismatchIsRejectedBeforeProviderOrOwner(){ObjectProvider<InitialLoanContractAuthority> a=mock(ObjectProvider.class);ObjectProvider<InitialLoanContractService> s=mock(ObjectProvider.class);StepVerifier.create(new InitialLoanContractController(a,s).link(app,action,new InitialLoanContractRequest(new UUID(0,3),null))).expectErrorMatches(e->e instanceof ResponseStatusException f&&f.getStatusCode().value()==400).verify();verifyNoInteractions(a,s);}
    @SuppressWarnings("unchecked") @Test void genericLate403CannotEraseOriginalActionUncertainty(){ObjectProvider<InitialLoanContractAuthority> a=mock(ObjectProvider.class);ObjectProvider<InitialLoanContractService> s=mock(ObjectProvider.class);InitialLoanContractService service=mock(InitialLoanContractService.class);when(s.getIfUnique()).thenReturn(service);when(service.link(eq(app),any())).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        when(a.getIfUnique()).thenReturn(new InitialLoanContractAuthority(){public <T> Mono<T> invoke(UUID app,UUID action,Purpose purpose,Supplier<Mono<T>> operation){return Mono.defer(operation);}public Mono<Scope> requireCurrent(UUID app,UUID action,Purpose purpose){return Mono.error(new AssertionError("Service fixture does not acquire authority"));}public Mono<Void> revalidate(Scope scope){return Mono.empty();}public Mono<Void> requireTransportContext(Scope scope){return Mono.empty();}});
        StepVerifier.create(new InitialLoanContractController(a,s).link(app,action,new InitialLoanContractRequest(action,null))).expectErrorMatches(e->e instanceof InitialLoanContractFailure f&&f.outcome()==InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN&&action.equals(f.actionId())).verify();verify(service).link(eq(app),any());}
}
