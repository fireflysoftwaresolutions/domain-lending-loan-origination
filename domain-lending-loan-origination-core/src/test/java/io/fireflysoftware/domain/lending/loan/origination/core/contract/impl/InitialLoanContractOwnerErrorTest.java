/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import static org.junit.jupiter.api.Assertions.*;
class InitialLoanContractOwnerErrorTest {
    private final UUID action=new UUID(0,1);private final Instant now=Instant.parse("2026-11-02T00:00:00Z");
    private InitialLoanContractAuthority.Scope scope(){return new InitialLoanContractAuthority.Scope(action,new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5),"https://issuer.example","human","opaque",InitialLoanContractAuthority.Purpose.LINK,now,0,now.plusSeconds(5));}
    private String body(String outcome){return "{\"actionId\":\""+action+"\",\"code\":\"CONTRACT_LINK_"+outcome+"\",\"outcome\":\""+outcome+"\",\"recovery\":\"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY\"}";}
    private InitialLoanContractFailure parsed(String body,MediaType type,int status){var headers=new HttpHeaders();headers.setContentType(type);return TokenExchangedInitialLoanContractOwnerPortImpl.writeFailure(scope(),WebClientResponseException.create(status,"owner",headers,body.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8));}
    @Test void exactTypedPreworkRefusalIsDistinctFromUncertainDispatch(){assertEquals(InitialLoanContractFailure.Outcome.REFUSED,parsed(body("REFUSED"),MediaType.APPLICATION_JSON,503).outcome());assertEquals(InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,parsed("",MediaType.APPLICATION_JSON,503).outcome());}
    @Test void duplicateAndTrailingBodiesNeverProveRollback(){String valid=body("REFUSED");assertEquals(InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,parsed(valid.substring(0,valid.length()-1)+",\"outcome\":\"REFUSED\"}",MediaType.APPLICATION_JSON,503).outcome());assertEquals(InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,parsed(valid+" {}",MediaType.APPLICATION_JSON,503).outcome());}
    @Test void wrongContentTypeActionAndStatusNeverResolveUncertainWrite(){assertEquals(InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,parsed(body("REFUSED"),MediaType.TEXT_PLAIN,503).outcome());assertEquals(InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,parsed(body("REFUSED").replace(action.toString(),new UUID(0,50).toString()),MediaType.APPLICATION_JSON,503).outcome());assertEquals(InitialLoanContractFailure.Outcome.OUTCOME_UNKNOWN,parsed(body("REFUSED"),MediaType.APPLICATION_JSON,409).outcome());}
    @Test void genuineCommittedAndConflictEnvelopesRetainExactOriginalAction(){var committed=parsed(body("COMMITTED_RESPONSE_UNAVAILABLE"),MediaType.APPLICATION_JSON,503);assertEquals(action,committed.actionId());assertEquals(InitialLoanContractFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE,committed.outcome());assertEquals(InitialLoanContractFailure.Outcome.CONFLICT,parsed(body("CONFLICT"),MediaType.APPLICATION_JSON,409).outcome());}
}
