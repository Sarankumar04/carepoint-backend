package com.example.doctorappointment.repository;

import com.example.doctorappointment.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {
    Page<DoctorAvailability> findByDoctorUserIdAndActiveTrue(long userId, Pageable pageable);

    Optional<DoctorAvailability> findByIdAndDoctorId(long id, long doctorId);

    List<DoctorAvailability> findByDoctorIdAndAvailableDateAndActiveTrueOrderByStartTime(
            long doctorId, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from DoctorAvailability a
            where a.doctor.id = :doctorId and a.availableDate = :date and a.active = true
              and a.startTime <= :start and a.endTime > :start
            """)
    Optional<DoctorAvailability> lockCoveringWindow(long doctorId, LocalDate date, LocalTime start);

    @Query("""
            select count(a) > 0 from DoctorAvailability a
            where a.doctor.id = :doctorId and a.availableDate = :date and a.active = true
              and (:excludedId is null or a.id <> :excludedId)
              and a.startTime < :end and a.endTime > :start
            """)
    boolean overlaps(long doctorId, LocalDate date, LocalTime start, LocalTime end, Long excludedId);
}
