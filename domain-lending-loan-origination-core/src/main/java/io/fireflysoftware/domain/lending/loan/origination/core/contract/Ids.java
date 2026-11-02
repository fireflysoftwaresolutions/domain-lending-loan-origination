/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract;
import java.util.UUID;
public final class Ids {
    private Ids(){}
    public static UUID require(UUID id){if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact nonzero owner identity required");return id;}
    public static String digest(String value){if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Opaque owner digest required");return value;}
}
