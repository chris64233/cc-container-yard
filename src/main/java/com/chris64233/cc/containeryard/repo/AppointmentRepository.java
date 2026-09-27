package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.Appointment;
import com.chris64233.cc.containeryard.domain.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.appointmentNo = :no")
    Optional<Appointment> findByAppointmentNoForUpdate(@Param("no") String appointmentNo);

    boolean existsByActiveContainerNo(String containerNo);

    List<Appointment> findAllByOrderByCreatedAtDesc();

    List<Appointment> findByWindowIdOrderById(Long windowId);

    List<Appointment> findByTargetStackCodeAndStatusOrderById(String stackCode, AppointmentStatus status);
}
