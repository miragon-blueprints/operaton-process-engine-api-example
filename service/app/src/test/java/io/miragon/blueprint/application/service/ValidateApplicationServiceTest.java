package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class ValidateApplicationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ValidateApplicationService underTest = new ValidateApplicationService(repository);

    @Test
    @DisplayName("validate loads a well-formed application without error")
    void validateLoadsAWellFormedApplicationWithoutError() {

        // given: a valid, solvent application in the repository
        LeasingApplication application = testLeasingApplication().build();
        given(repository.findById(application.id())).willReturn(application);

        // when: the application is validated
        underTest.validate(application.id());

        // then: the application was loaded and accepted
        then(repository).should().findById(application.id());
        then(repository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("validate rejects an application without income")
    void validateRejectsAnApplicationWithoutIncome() {

        // given: an application with zero monthly net income
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(0.0).build();
        given(repository.findById(application.id())).willReturn(application);

        // when / then: validation surfaces the application as invalid
        assertThatThrownBy(() -> underTest.validate(application.id()))
            .isInstanceOf(ApplicationInvalidException.class);
    }
}
