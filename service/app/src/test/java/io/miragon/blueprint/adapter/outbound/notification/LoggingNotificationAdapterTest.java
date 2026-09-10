package io.miragon.blueprint.adapter.outbound.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingNotificationAdapterTest {

    private final LoggingNotificationAdapter underTest = new LoggingNotificationAdapter();

    @Test
    @DisplayName("send logs the notification without error")
    void sendLogsTheNotificationWithoutError() {

        // given: a subject and an application
        var application = testLeasingApplication().build();

        // when / then: sending the notification completes without throwing
        assertThatCode(() -> underTest.send("Please review and sign your leasing contract", application))
            .doesNotThrowAnyException();
    }
}
