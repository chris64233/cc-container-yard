package com.chris64233.cc.containeryard.repo;

import com.chris64233.cc.containeryard.domain.SlotReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlotReservationRepository extends JpaRepository<SlotReservation, Long> {

    Optional<SlotReservation> findByAppointmentId(Long appointmentId);

    List<SlotReservation> findByActiveSlotKeyIsNotNull();

    List<SlotReservation> findByStackCodeAndActiveSlotKeyIsNotNull(String stackCode);
}
