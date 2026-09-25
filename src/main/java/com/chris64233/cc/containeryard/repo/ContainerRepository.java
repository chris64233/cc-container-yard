package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.Container;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long> {

    Optional<Container> findByContainerNo(String containerNo);

    List<Container> findByContainerNoIn(Collection<String> containerNos);

    List<Container> findByStack_CodeInOrderByStack_CodeAscTierAsc(Collection<String> stackCodes);

    List<Container> findByStack_CodeOrderByTierAsc(String stackCode);
}
