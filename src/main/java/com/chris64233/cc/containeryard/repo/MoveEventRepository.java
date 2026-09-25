package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.MoveEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoveEventRepository extends JpaRepository<MoveEvent, Long> {

    List<MoveEvent> findByPlanIdOrderBySeq(Long planId);

    List<MoveEvent> findAllByOrderById();
}
