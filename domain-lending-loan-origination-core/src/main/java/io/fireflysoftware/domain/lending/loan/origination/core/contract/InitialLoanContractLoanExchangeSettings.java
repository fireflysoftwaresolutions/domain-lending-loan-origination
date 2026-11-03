/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import java.net.URI;
import java.util.UUID;
public record InitialLoanContractLoanExchangeSettings(String issuer,String incomingAudience,String incomingClientId,String platformTenant,
        String loanAudience,String loanClientId,UUID loanResourceTenantId,UUID contractResourceTenantId,URI loanEndpoint,boolean ownedLoopbackSandbox){
    public InitialLoanContractLoanExchangeSettings{
        for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,loanAudience,loanClientId})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Exact original Contract exchange profile required");
        for(UUID id:new UUID[]{loanResourceTenantId,contractResourceTenantId})Ids.require(id);LoanIntakeExchangeSettings.endpoint(loanEndpoint,ownedLoopbackSandbox);
    }
    @Override public String toString(){return "InitialLoanContractLoanExchangeSettings[redacted]";}
}
