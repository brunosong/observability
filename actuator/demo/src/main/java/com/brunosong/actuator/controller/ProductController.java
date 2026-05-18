package com.brunosong.actuator.controller;

import com.brunosong.actuator.service.CpuLoadService;
import com.brunosong.actuator.service.DbLockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Long LOCK_ID = 4L;
    private static final String NAME = "product";

    private final DbLockService dbLockService;
    private final CpuLoadService cpuLoadService;

    public ProductController(DbLockService dbLockService, CpuLoadService cpuLoadService) {
        this.dbLockService = dbLockService;
        this.cpuLoadService = cpuLoadService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", 1, "name", "Laptop", "price", 1499.00),
                Map.of("id", 2, "name", "Headphones", "price", 199.99),
                Map.of("id", 3, "name", "Keyboard", "price", 89.50)
        );
    }

    @GetMapping("/slow")
    public Map<String, Object> slow() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(300, 800));
        return Map.of("status", "slow response", "took", "300-800ms");
    }

    @GetMapping("/flaky")
    public ResponseEntity<Map<String, Object>> flaky() {
        if (ThreadLocalRandom.current().nextDouble() < 0.25) {
            return ResponseEntity.status(500).body(Map.of("error", "simulated failure"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
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
}
