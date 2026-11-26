package com.example.doctorappointment.repository;

import com.example.doctorappointment.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(long userId);

    @Query("select p from Patient p join fetch p.user u where (:active is null or u.active = :active)")
    Page<Patient> search(Boolean active, Pageable pageable);
}
