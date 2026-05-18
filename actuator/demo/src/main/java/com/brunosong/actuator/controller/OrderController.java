package com.brunosong.actuator.controller;

import com.brunosong.actuator.service.CpuLoadService;
import com.brunosong.actuator.service.DbLockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Long LOCK_ID = 3L;
    private static final String NAME = "order";

    private final DbLockService dbLockService;
    private final CpuLoadService cpuLoadService;

    public OrderController(DbLockService dbLockService, CpuLoadService cpuLoadService) {
        this.dbLockService = dbLockService;
        this.cpuLoadService = cpuLoadService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", 1001, "userId", 1, "amount", 49.99, "status", "PAID"),
                Map.of("id", 1002, "userId", 2, "amount", 129.50, "status", "PENDING"),
                Map.of("id", 1003, "userId", 1, "amount", 19.95, "status", "SHIPPED")
        );
    }

    @GetMapping("/db-lock")
    public Map<String, Object> dbLock(@RequestParam(defaultValue = "1500") long holdMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        long counter = dbLockService.lockAndHold(LOCK_ID, holdMs);
        return Map.of(
                "controller", NAME,
                "counter", counter,
                "holdMs", holdMs,
                "tookMs", System.currentTimeMillis() - start
        );
    }

    @GetMapping("/cpu-spike")
    public Map<String, Object> cpuSpike(@RequestParam(defaultValue = "2000") long durationMs,
                                        @RequestParam(defaultValue = "0") int threads) throws InterruptedException {
        int workers = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        long start = System.currentTimeMillis();
        long acc = cpuLoadService.burn(durationMs, workers);
        return Map.of(
                "controller", NAME,
                "durationMs", durationMs,
                "threads", workers,
                "tookMs", System.currentTimeMillis() - start,
                "acc", acc
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable int id) {
        return Map.of(
                "id", id,
                "amount", ThreadLocalRandom.current().nextDouble(10, 500),
                "status", "PAID"
        );
    }
}
