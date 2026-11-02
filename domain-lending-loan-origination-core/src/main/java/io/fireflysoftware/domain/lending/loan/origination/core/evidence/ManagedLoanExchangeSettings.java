/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import java.net.URI;
import java.util.UUID;
public record ManagedLoanExchangeSettings(String issuer,String incomingAudience,String incomingClientId,String platformTenant,
        String loanAudience,String loanClientId,UUID loanResourceTenantId,UUID documentResourceTenantId,URI loanEndpoint,boolean ownedLoopbackSandbox){
    public ManagedLoanExchangeSettings{
        for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,loanAudience,loanClientId})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Exact original Loan exchange profile required");
        for(UUID id:new UUID[]{loanResourceTenantId,documentResourceTenantId})if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact configured owner namespaces required");LoanIntakeExchangeSettings.endpoint(loanEndpoint,ownedLoopbackSandbox);
    }
    @Override public String toString(){return "ManagedLoanExchangeSettings[redacted]";}
}
