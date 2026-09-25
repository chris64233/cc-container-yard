package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.YardStack;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YardStackRepository extends JpaRepository<YardStack, Long> {

    Optional<YardStack> findByCode(String code);

    List<YardStack> findAllByOrderByCode();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from YardStack s where s.code in :codes order by s.code")
    List<YardStack> findAllByCodeInForUpdate(@Param("codes") Collection<String> codes);
}
