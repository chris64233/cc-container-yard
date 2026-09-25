package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.YardStack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YardStackRepository extends JpaRepository<YardStack, Long> {

    Optional<YardStack> findByCode(String code);

    List<YardStack> findByCodeIn(Collection<String> codes);

    List<YardStack> findAllByOrderByCodeAsc();
}
