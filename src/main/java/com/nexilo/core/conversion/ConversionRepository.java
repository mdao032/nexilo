package com.nexilo.core.conversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConversionRepository extends JpaRepository<ConversionRecord, UUID> {
    List<ConversionRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT c FROM ConversionRecord c WHERE c.status = 'DONE' AND c.expiresAt <= :now")
    List<ConversionRecord> findExpired(@Param("now") Instant now);
}

