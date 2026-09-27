package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.Container;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long> {

    Optional<Container> findByContainerNo(String containerNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Container c where c.containerNo = :no")
    Optional<Container> findByContainerNoForUpdate(@Param("no") String no);

    List<Container> findByStackCodeOrderByTier(String stackCode);
}
