package com.brunosong.actuator.config;

import com.brunosong.actuator.entity.LockResource;
import com.brunosong.actuator.repository.LockResourceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final LockResourceRepository repository;

    public DataInitializer(LockResourceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        repository.save(new LockResource(1L, "hello"));
        repository.save(new LockResource(2L, "user"));
        repository.save(new LockResource(3L, "order"));
        repository.save(new LockResource(4L, "product"));
    }
}
