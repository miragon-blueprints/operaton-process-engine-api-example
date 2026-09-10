package io.miragon.blueprint.domain.leasing;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Shared test builder — start from a valid, solvent application and override only what a test cares about. */
public final class TestObjectBuilder {

    private TestObjectBuilder() {
    }

    public static LeasingApplicationBuilder testLeasingApplication() {
        return new LeasingApplicationBuilder();
    }

    public static final class LeasingApplicationBuilder {

        private ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        private CustomerName customerName = new CustomerName("John Doe");
        private Email email = new Email("john.doe@test.com");
        private int age = 35;
        private double monthlyNetIncome = 3500.0;
        private BikeId bikeId = new BikeId("BIKE-900");
        private LeasingStatus status = LeasingStatus.RECEIVED;
        private LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        private @Nullable OrderId orderId = null;
        private @Nullable ContractId contractId = null;

        private LeasingApplicationBuilder() {
        }

        public LeasingApplicationBuilder id(ApplicationId id) {
            this.id = id;
            return this;
        }

        public LeasingApplicationBuilder customerName(CustomerName customerName) {
            this.customerName = customerName;
            return this;
        }

        public LeasingApplicationBuilder email(Email email) {
            this.email = email;
            return this;
        }

        public LeasingApplicationBuilder age(int age) {
            this.age = age;
            return this;
        }

        public LeasingApplicationBuilder monthlyNetIncome(double monthlyNetIncome) {
            this.monthlyNetIncome = monthlyNetIncome;
            return this;
        }

        public LeasingApplicationBuilder bikeId(BikeId bikeId) {
            this.bikeId = bikeId;
            return this;
        }

        public LeasingApplicationBuilder status(LeasingStatus status) {
            this.status = status;
            return this;
        }

        public LeasingApplicationBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public LeasingApplicationBuilder orderId(@Nullable OrderId orderId) {
            this.orderId = orderId;
            return this;
        }

        public LeasingApplicationBuilder contractId(@Nullable ContractId contractId) {
            this.contractId = contractId;
            return this;
        }

        public LeasingApplication build() {
            return new LeasingApplication(
                id, customerName, email, age, monthlyNetIncome, bikeId, status, createdAt, orderId, contractId);
        }
    }
}
