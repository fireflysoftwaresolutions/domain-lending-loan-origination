/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.intake.impl;

import io.fireflysoftware.domain.lending.loan.origination.core.intake.*;
import io.fireflysoftware.core.lending.origination.sdk.model.ApplicationPartyDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Controlled typed read-model and completion contracts; no authenticated transport or three-Main acceptance. */
class DraftLoanIntakeBorrowerViewContractTest {
    private final DraftLoanIntakeRequest request=new DraftLoanIntakeRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("1234.50"),24,"EUR","vehicle");
    private final DraftLoanIntakeReceipt receipt=new DraftLoanIntakeReceipt(request.requestId(),UUID.randomUUID(),request.simulationId(),request.productId(),request.partyId(),request.requestedAmount(),request.term(),request.currency(),request.purpose(),"DRAFT","PENDING_DOCUMENTS",Instant.parse("2026-11-01T10:00:00Z"),false,"a".repeat(64));
    private final UUID link=UUID.randomUUID(),role=UUID.randomUUID();
    private ApplicationPartyDTO primary(){return new ApplicationPartyDTO(link).loanApplicationId(receipt.applicationId()).partyId(request.partyId()).roleCodeId(role).isPrimary(true);}
    @Test void genuinePrimaryAndBorrowerViewsMustShareTheExactPersistedLink(){
        TokenExchangedCoreDraftIntakePortImpl.requirePrimary(request,receipt,primary());
        TokenExchangedCoreDraftIntakePortImpl.requireBorrowerView(request,receipt,primary(),List.of(primary()));
        assertThat(primary().getEmploymentTypeId()).isNull();assertThat(primary().getAnnualIncome()).isNull();
    }
    @Test void wrongApplicationPartyRoleOrPrimaryFlagCannotCompleteTheReadback(){
        for(var row:List.of(primary().loanApplicationId(UUID.randomUUID()),primary().partyId(UUID.randomUUID()),primary().roleCodeId(new UUID(0,0)),primary().isPrimary(false)))
            assertThatThrownBy(()->TokenExchangedCoreDraftIntakePortImpl.requirePrimary(request,receipt,row)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void missingDuplicateOrDifferentLinkBorrowerProjectionCannotCompleteTheReadback(){
        for(var rows:List.of(List.<ApplicationPartyDTO>of(),List.of(primary(),primary()),List.of(new ApplicationPartyDTO(UUID.randomUUID()).loanApplicationId(receipt.applicationId()).partyId(request.partyId()).roleCodeId(role).isPrimary(true))))
            assertThatThrownBy(()->TokenExchangedCoreDraftIntakePortImpl.requireBorrowerView(request,receipt,primary(),rows)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void borrowerHistoryHasAFiniteRecordBoundWithoutChangingThePublicListContract(){
        var rows=new ArrayList<ApplicationPartyDTO>();rows.add(primary());
        rows.add(new ApplicationPartyDTO(UUID.randomUUID()).loanApplicationId(receipt.applicationId()).partyId(request.partyId()).roleCodeId(UUID.randomUUID()).isPrimary(false));
        for(int index=2;index<64;index++)rows.add(new ApplicationPartyDTO(UUID.randomUUID()).loanApplicationId(UUID.randomUUID()).partyId(request.partyId()).roleCodeId(role).isPrimary(true));
        TokenExchangedCoreDraftIntakePortImpl.requireBorrowerView(request,receipt,primary(),rows);
        rows.add(new ApplicationPartyDTO(UUID.randomUUID()).loanApplicationId(UUID.randomUUID()).partyId(request.partyId()).roleCodeId(role).isPrimary(true));
        assertThatThrownBy(()->TokenExchangedCoreDraftIntakePortImpl.requireBorrowerView(request,receipt,primary(),rows)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->TokenExchangedCoreDraftIntakePortImpl.requireBorrowerView(request,receipt,primary(),List.of(primary(),primary().loanApplicationId(UUID.randomUUID()).partyId(UUID.randomUUID())))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void knownCommitAfterReadFailureRetainsOriginalRecoveryIdentityWithoutClaimingRefusal(){
        var failure=TokenExchangedCoreDraftIntakePortImpl.completedUnavailable(request,receipt,true);
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);assertThat(failure.getCode()).isEqualTo("LENDING_INTAKE_COMMITTED_RESPONSE_UNAVAILABLE");
        assertThat(failure.getMetadata()).containsEntry("requestId",request.requestId().toString()).containsEntry("outcome","COMMITTED")
            .containsEntry("recovery","SAME_REQUEST_ID_EXACT_REPLAY").containsEntry("intentDigest",receipt.intentDigest());
        assertThat(failure.getCause()).isNull();assertThat(failure.getMessage()).doesNotContain("401","403","refused");
    }
    @Test void lostCurrentAuthorityOmitsOwnerDigestButNeverChangesTheOriginalAction(){
        var failure=TokenExchangedCoreDraftIntakePortImpl.completedUnavailable(request,receipt,false);
        assertThat(failure.getCode()).isEqualTo("LENDING_INTAKE_COMMITTED_RESPONSE_UNAVAILABLE");assertThat(failure.getMetadata()).doesNotContainKey("intentDigest")
            .containsEntry("requestId",request.requestId().toString()).containsEntry("outcome","COMMITTED").containsEntry("recovery","SAME_REQUEST_ID_EXACT_REPLAY");
    }
}
