package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.Container;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long> {

    Optional<Container> findByContainerNo(String containerNo);

    List<Container> findByStackCodeOrderByTier(String stackCode);
}
