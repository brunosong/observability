package com.brunosong.actuator.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Service
public class ConnectionHoldService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public long holdConnection(long durationMillis) throws InterruptedException {
        Number ping = (Number) entityManager.createNativeQuery("SELECT 1").getSingleResult();
        Thread.sleep(durationMillis);
        return ping.longValue();
    }
}
