package com.acme.payments.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Query(value = "select * from outbox_events where published_at is null and failed_at is null order by created_at for update skip locked limit :limit", nativeQuery = true)
    List<OutboxEventEntity> lockNextBatch(@Param("limit") int limit);
}
