/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.contract.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.contract.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class InitialLoanContractConfigurationTest {
    @Test void managedContractCannotEnableItselfOrSupplyDefaultBusinessOwners(){new ApplicationContextRunner().withUserConfiguration(InitialLoanContractConfiguration.class).run(context->{assertThat(context).hasNotFailed();assertThat(context).doesNotHaveBean(InitialLoanContractService.class);assertThat(context).doesNotHaveBean(InitialLoanContractAuthority.class);assertThat(context).doesNotHaveBean(InitialLoanContractLoanPort.class);assertThat(context).doesNotHaveBean(InitialLoanContractOwnerPort.class);});}
    @Test void enabledManagedContractRequiresExplicitTrustAndOwnerSettings(){new ApplicationContextRunner().withUserConfiguration(InitialLoanContractConfiguration.class).withPropertyValues("lending.initial-primary-contract.enabled=true").run(context->assertThat(context).hasFailed());}
}
