package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    Optional<Appointment> findByActiveContainerKey(String activeContainerKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.appointmentNo = :appointmentNo")
    Optional<Appointment> findByAppointmentNoForUpdate(@Param("appointmentNo") String appointmentNo);
}
