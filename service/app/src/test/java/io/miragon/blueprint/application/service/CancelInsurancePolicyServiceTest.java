package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.InsurancePort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class CancelInsurancePolicyServiceTest {

    private final InsurancePort insurance = mock(InsurancePort.class);
    private final CancelInsurancePolicyService underTest = new CancelInsurancePolicyService(insurance);

    @Test
    @DisplayName("cancelPolicy delegates the compensation to the insurer")
    void cancelPolicyDelegatesTheCompensationToTheInsurer() {
        // given: an application whose policy must be revoked
        LeasingApplication application = testLeasingApplication().build();
        // when: the policy is cancelled
        underTest.cancelPolicy(application.id());
        // then: the insurance out-port revokes the policy
        then(insurance).should().cancelPolicy(application.id());
        then(insurance).shouldHaveNoMoreInteractions();
    }
}
