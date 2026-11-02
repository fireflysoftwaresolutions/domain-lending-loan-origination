/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence;

import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;

public final class ManagedEvidenceFailure extends BusinessException {
    public enum Outcome { OBSERVATION_UNAVAILABLE,DOCUMENT_OUTCOME_UNKNOWN,DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE,
        DOCUMENT_STORED_ASSOCIATION_UNKNOWN,ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE }
    private final Outcome outcome;private final UUID actionId;
    public ManagedEvidenceFailure(UUID actionId,Outcome outcome,Map<String,Object> currentDetails){
        super(HttpStatus.SERVICE_UNAVAILABLE,"MANAGED_EVIDENCE_"+Objects.requireNonNull(outcome).name(),"Managed evidence completion is unavailable",metadata(actionId,outcome,currentDetails));this.outcome=outcome;this.actionId=Objects.requireNonNull(actionId);
    }
    public UUID actionId(){return actionId;}
    public Outcome outcome(){return outcome;}
    private static Map<String,Object> metadata(UUID action,Outcome outcome,Map<String,Object> details){
        var result=new LinkedHashMap<String,Object>();result.put("actionId",Objects.requireNonNull(action).toString());result.put("outcome",outcome.name());result.put("recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY");
        for(String key:List.of("documentId","contentDigest","documentIntentDigest","associationId"))if(details.containsKey(key))result.put(key,details.get(key));return result;
    }
}
