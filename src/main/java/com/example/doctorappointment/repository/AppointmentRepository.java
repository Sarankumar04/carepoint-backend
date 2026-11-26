package com.example.doctorappointment.repository;

import com.example.doctorappointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import com.example.doctorappointment.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> lockById(long id);

    @Query("select a.doctor.id from Appointment a where a.id = :id and a.patient.user.id = :userId")
    Optional<Long> findOwnedDoctorId(long id, long userId);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId and a.appointmentDate = :date and a.status = :status
              and (:excludedId is null or a.id <> :excludedId)
              and a.startTime < :end and a.endTime > :start
            """)
    boolean overlaps(long doctorId, LocalDate date, LocalTime start, LocalTime end,
                     AppointmentStatus status, Long excludedId);

    List<Appointment> findByDoctorIdAndAppointmentDateAndStatus(long doctorId, LocalDate date,
                                                               AppointmentStatus status);

    List<Appointment> findByAvailabilityIdAndStatus(long availabilityId, AppointmentStatus status);

    @Query("""
            select a from Appointment a
            join fetch a.doctor d join fetch d.user du
            join fetch a.patient p join fetch p.user pu
            where (:doctorId is null or d.id = :doctorId)
              and (:patientId is null or p.id = :patientId)
              and (:doctorUserId is null or du.id = :doctorUserId)
              and (:patientUserId is null or pu.id = :patientUserId)
              and (:status is null or a.status = :status)
              and (:date is null or a.appointmentDate = :date)
              and (:fromDate is null or a.appointmentDate >= :fromDate)
              and (:toDate is null or a.appointmentDate <= :toDate)
            """)
    Page<Appointment> search(Long doctorId, Long patientId, Long doctorUserId, Long patientUserId,
                             AppointmentStatus status, LocalDate date, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    long countByAppointmentDate(LocalDate date);

    @Query("select a.status as status, count(a) as total from Appointment a group by a.status")
    List<StatusCount> countGroupedByStatus();

    interface StatusCount {
        AppointmentStatus getStatus();
        long getTotal();
    }
}
