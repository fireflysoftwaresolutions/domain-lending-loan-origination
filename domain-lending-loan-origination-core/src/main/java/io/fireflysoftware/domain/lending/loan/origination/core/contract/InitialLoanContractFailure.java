/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class InitialLoanContractFailure extends BusinessException {
    public enum Outcome {REFUSED,CONFLICT,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE,OBSERVATION_UNAVAILABLE}
    private final UUID action;private final Outcome outcome;
    public InitialLoanContractFailure(UUID action,Outcome outcome){super(outcome==Outcome.CONFLICT?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE,"LENDING_INITIAL_CONTRACT_"+outcome,"Original initial loan contract response unavailable",Map.of("actionId",Ids.require(action).toString(),"outcome",outcome.name(),"recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.action=action;this.outcome=Objects.requireNonNull(outcome);}
    public UUID actionId(){return action;}public Outcome outcome(){return outcome;}
}
