package com.example.doctorappointment.repository;

import com.example.doctorappointment.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUserId(long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> lockById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.user.id = :userId")
    Optional<Doctor> lockByUserId(long userId);

    @Query("""
            select d from Doctor d join fetch d.user u join fetch d.department dp
            where (:includeInactive = true or (u.active = true and dp.active = true))
              and (:departmentId is null or dp.id = :departmentId)
              and (:active is null or u.active = :active)
              and (:specialization is null or lower(d.specialization) like lower(:specialization) escape '!')
              and (:search is null or lower(u.fullName) like lower(:search) escape '!'
                   or lower(d.specialization) like lower(:search) escape '!')
            """)
    Page<Doctor> search(boolean includeInactive, Long departmentId, Boolean active,
                        String specialization, String search, Pageable pageable);
}
