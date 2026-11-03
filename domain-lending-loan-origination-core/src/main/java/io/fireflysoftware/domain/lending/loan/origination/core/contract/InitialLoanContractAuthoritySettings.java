/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.time.Instant;
import java.util.UUID;

/** Explicit server-owned target namespaces; neither request metadata nor a party identifier chooses an owner tenant. */
public record InitialLoanContractAuthoritySettings(String issuer,String audience,String incomingClientId,String platformTenant,
        UUID loanResourceTenantId,UUID contractResourceTenantId,Instant notAfter) {
    public InitialLoanContractAuthoritySettings {
        for(String value:new String[]{issuer,audience,incomingClientId,platformTenant})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit initial loan contract trust configuration required");
        for(UUID value:new UUID[]{loanResourceTenantId,contractResourceTenantId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact owner resource namespaces required");
        if(notAfter==null)throw new IllegalArgumentException("Finite current owner namespace mapping required");
    }
    public void requireCurrent(Instant now){if(!now.isBefore(notAfter))throw new IllegalStateException("Current owner namespace mapping required");}
    @Override public String toString(){return "InitialLoanContractAuthoritySettings[redacted]";}
}
