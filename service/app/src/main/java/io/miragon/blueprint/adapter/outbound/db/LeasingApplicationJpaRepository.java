package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeasingApplicationJpaRepository extends JpaRepository<LeasingApplicationEntity, UUID> {

    @Nullable
    LeasingApplicationEntity findByApplicationId(UUID id);

    Page<LeasingApplicationEntity> findAllByStatus(LeasingStatus status, Pageable pageable);
}
