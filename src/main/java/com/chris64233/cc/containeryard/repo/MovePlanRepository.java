package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.MovePlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MovePlanRepository extends JpaRepository<MovePlan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MovePlan p where p.id = :id")
    Optional<MovePlan> findByIdForUpdate(@Param("id") Long id);
}
