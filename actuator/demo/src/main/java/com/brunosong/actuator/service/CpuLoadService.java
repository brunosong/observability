package com.brunosong.actuator.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CpuLoadService {

    public long burn(long durationMillis, int threads) throws InterruptedException {
        int workers = Math.max(1, threads);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        AtomicLong total = new AtomicLong();
        long deadline = System.currentTimeMillis() + durationMillis;

        try {
            for (int i = 0; i < workers; i++) {
                executor.submit(() -> total.addAndGet(busyLoop(deadline)));
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(durationMillis + 5000, TimeUnit.MILLISECONDS);
        }
        return total.get();
    }

    private long busyLoop(long deadline) {
        long acc = 1L;
        while (System.currentTimeMillis() < deadline) {
            for (int i = 2; i < 5_000; i++) {
                acc = (acc * i) ^ (acc >>> 3);
            }
        }
        return acc;
    }
}
