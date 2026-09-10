package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.domain.leasing.LeasingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity(name = "leasing_application")
public class LeasingApplicationEntity {

    @Id
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "age", nullable = false)
    private int age;

    @Column(name = "monthly_net_income", nullable = false)
    private double monthlyNetIncome;

    @Column(name = "bike_id", nullable = false)
    private String bikeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeasingStatus status;

    @Column(name = "order_id")
    private @Nullable String orderId;

    @Column(name = "contract_id")
    private @Nullable String contractId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LeasingApplicationEntity() {
    }

    public LeasingApplicationEntity(
        UUID applicationId,
        String customerName,
        String email,
        int age,
        double monthlyNetIncome,
        String bikeId,
        LeasingStatus status,
        @Nullable String orderId,
        @Nullable String contractId,
        LocalDateTime createdAt
    ) {
        this.applicationId = applicationId;
        this.customerName = customerName;
        this.email = email;
        this.age = age;
        this.monthlyNetIncome = monthlyNetIncome;
        this.bikeId = bikeId;
        this.status = status;
        this.orderId = orderId;
        this.contractId = contractId;
        this.createdAt = createdAt;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getMonthlyNetIncome() {
        return monthlyNetIncome;
    }

    public void setMonthlyNetIncome(double monthlyNetIncome) {
        this.monthlyNetIncome = monthlyNetIncome;
    }

    public String getBikeId() {
        return bikeId;
    }

    public void setBikeId(String bikeId) {
        this.bikeId = bikeId;
    }

    public LeasingStatus getStatus() {
        return status;
    }

    public void setStatus(LeasingStatus status) {
        this.status = status;
    }

    public @Nullable String getOrderId() {
        return orderId;
    }

    public void setOrderId(@Nullable String orderId) {
        this.orderId = orderId;
    }

    public @Nullable String getContractId() {
        return contractId;
    }

    public void setContractId(@Nullable String contractId) {
        this.contractId = contractId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
