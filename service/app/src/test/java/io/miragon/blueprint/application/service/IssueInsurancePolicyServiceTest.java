package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.InsurancePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class IssueInsurancePolicyServiceTest {

    private final InsurancePort insurance = mock(InsurancePort.class);
    private final IssueInsurancePolicyService underTest = new IssueInsurancePolicyService(insurance);

    @Test
    @DisplayName("issuePolicy delegates to the insurer")
    void issuePolicyDelegatesToTheInsurer() {
        // given: an eligible application
        var application = testLeasingApplication().build();
        // when: the policy is issued
        underTest.issuePolicy(application.id());
        // then: the insurance out-port binds the policy
        then(insurance).should().issuePolicy(application.id());
        then(insurance).shouldHaveNoMoreInteractions();
    }
}
