package com.brunosong.actuator.controller;

import com.brunosong.actuator.service.CpuLoadService;
import com.brunosong.actuator.service.DbLockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    private static final Long LOCK_ID = 1L;
    private static final String NAME = "hello";

    private final DbLockService dbLockService;
    private final CpuLoadService cpuLoadService;

    public HelloController(DbLockService dbLockService, CpuLoadService cpuLoadService) {
        this.dbLockService = dbLockService;
        this.cpuLoadService = cpuLoadService;
    }

    @GetMapping
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello, Actuator!",
                "timestamp", System.currentTimeMillis()
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

    @GetMapping("/{name}")
    public Map<String, Object> helloName(String name) {
        return Map.of("message", "Hello, " + name + "!");
    }
}
