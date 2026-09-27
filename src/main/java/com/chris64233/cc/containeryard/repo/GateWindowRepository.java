package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.GateWindow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GateWindowRepository extends JpaRepository<GateWindow, Long> {

    List<GateWindow> findAllByOrderByStartAtAsc();

    @Query("select w from GateWindow w where w.startAt < :to and w.endAt > :from order by w.startAt")
    List<GateWindow> findOverlapping(@Param("from") Instant from, @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from GateWindow w where w.id = :id")
    Optional<GateWindow> findByIdForUpdate(@Param("id") Long id);
}
