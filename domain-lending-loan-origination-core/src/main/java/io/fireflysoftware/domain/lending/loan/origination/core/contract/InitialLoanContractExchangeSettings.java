/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import java.net.URI;
import java.util.UUID;
public record InitialLoanContractExchangeSettings(String issuer,String incomingAudience,String incomingClientId,String platformTenant,
        String contractAudience,String contractClientId,UUID loanResourceTenantId,UUID contractResourceTenantId,UUID roleInContractId,URI contractEndpoint,boolean ownedLoopbackSandbox){
    public InitialLoanContractExchangeSettings{
        for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,contractAudience,contractClientId})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Exact original Contract exchange profile required");
        for(UUID id:new UUID[]{loanResourceTenantId,contractResourceTenantId,roleInContractId})Ids.require(id);LoanIntakeExchangeSettings.endpoint(contractEndpoint,ownedLoopbackSandbox);
    }
    @Override public String toString(){return "InitialLoanContractExchangeSettings[redacted]";}
}
