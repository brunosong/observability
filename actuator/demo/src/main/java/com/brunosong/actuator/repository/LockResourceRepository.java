package com.brunosong.actuator.repository;

import com.brunosong.actuator.entity.LockResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;

public interface LockResourceRepository extends JpaRepository<LockResource, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "15000"))
    @Query("select r from LockResource r where r.id = :id")
    LockResource lockById(@Param("id") Long id);
}
