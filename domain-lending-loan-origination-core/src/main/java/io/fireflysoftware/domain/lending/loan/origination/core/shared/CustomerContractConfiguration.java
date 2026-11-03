/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.shared;
import io.fireflysoftware.domain.lending.loan.origination.core.shared.impl.UnavailableCustomerContractLinkImpl;
import org.springframework.context.annotation.*;
/** No default fabricated business binding; the real operation uses its own signed original-action service. */
@Configuration(proxyBeanMethods=false)
public class CustomerContractConfiguration {
    @Bean public CustomerContractLink legacyCustomerContractLink(){return new UnavailableCustomerContractLinkImpl();}
}
