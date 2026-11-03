/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;
import io.fireflysoftware.domain.lending.loan.origination.core.intake.LoanIntakeExchangeSettings;
import java.net.URI;
import java.util.UUID;
public record ManagedDocumentExchangeSettings(String issuer,String incomingAudience,String incomingClientId,String platformTenant,
        String documentAudience,String documentClientId,UUID documentResourceTenantId,URI documentEndpoint,boolean ownedLoopbackSandbox){
    public ManagedDocumentExchangeSettings{
        for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,documentAudience,documentClientId})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Exact original document exchange profile required");
        if(documentResourceTenantId==null||documentResourceTenantId.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact configured document namespace required");LoanIntakeExchangeSettings.endpoint(documentEndpoint,ownedLoopbackSandbox);
    }
    @Override public String toString(){return "ManagedDocumentExchangeSettings[redacted]";}
}
