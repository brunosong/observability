package com.brunosong.actuator.service;

import com.brunosong.actuator.entity.LockResource;
import com.brunosong.actuator.repository.LockResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DbLockService {

    private final LockResourceRepository repository;

    public DbLockService(LockResourceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public long lockAndHold(Long id, long holdMillis) throws InterruptedException {
        LockResource resource = repository.lockById(id);
        if (resource == null) {
            throw new IllegalStateException("lock resource not found: " + id);
        }
        Thread.sleep(holdMillis);
        resource.increment();
        return resource.getCounter();
    }
}
