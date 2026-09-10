package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.PendingClarification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class GetPendingClarificationsServiceTest {

    private final TaskInboxPort taskInbox = mock(TaskInboxPort.class);
    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final GetPendingClarificationsService underTest =
        new GetPendingClarificationsService(taskInbox, repository, bikePortfolio);

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private final LocalDateTime waitingSince = LocalDateTime.of(2026, 8, 17, 9, 30);

    @Test
    @DisplayName("enriches each open task with its application and requested bike model")
    void enrichesEachOpenTaskWithItsApplicationAndRequestedBikeModel() {
        // given: one open clarification whose application requested BIKE-OOS
        LeasingApplication application = testLeasingApplication().id(id).bikeId(new BikeId("BIKE-OOS")).build();
        given(taskInbox.findOpenClarifications())
            .willReturn(List.of(new TaskInboxPort.OpenClarification(id, waitingSince)));
        given(repository.findById(id)).willReturn(application);
        given(bikePortfolio.findAllByIds(List.of(new BikeId("BIKE-OOS"))))
            .willReturn(List.of(new Bike(new BikeId("BIKE-OOS"), "Mountain Trail 600")));

        // when: the inbox is read
        List<PendingClarification> result = underTest.pending();

        // then: the pending clarification carries who, which bike (+ model) and since when — but no task id
        assertThat(result).hasSize(1);
        PendingClarification pending = result.getFirst();
        assertThat(pending.applicationId()).isEqualTo(id);
        assertThat(pending.customerName()).isEqualTo(application.customerName());
        assertThat(pending.requestedBikeId()).isEqualTo(new BikeId("BIKE-OOS"));
        assertThat(pending.requestedBikeModel()).isEqualTo("Mountain Trail 600");
        assertThat(pending.waitingSince()).isEqualTo(waitingSince);
    }

    @Test
    @DisplayName("skips tasks whose application can no longer be found")
    void skipsTasksWhoseApplicationCanNoLongerBeFound() {
        // given: an open task pointing at a vanished application
        given(taskInbox.findOpenClarifications())
            .willReturn(List.of(new TaskInboxPort.OpenClarification(id, waitingSince)));
        given(repository.findById(id)).willReturn(null);
        given(bikePortfolio.findAllByIds(List.of())).willReturn(List.of());

        // when / then: the orphan task is dropped rather than surfacing a half-built row
        assertThat(underTest.pending()).isEmpty();
        then(bikePortfolio).should().findAllByIds(List.of());
    }

    @Test
    @DisplayName("returns nothing when no clarification is open")
    void returnsNothingWhenNoClarificationIsOpen() {
        // given: an empty task list
        given(taskInbox.findOpenClarifications()).willReturn(List.of());
        given(bikePortfolio.findAllByIds(List.of())).willReturn(List.of());

        // when / then: the inbox is empty
        assertThat(underTest.pending()).isEmpty();
    }
}
