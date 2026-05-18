package com.brunosong.actuator.controller;

import com.brunosong.actuator.service.CpuLoadService;
import com.brunosong.actuator.service.DbLockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Long LOCK_ID = 2L;
    private static final String NAME = "user";

    private static final List<Map<String, Object>> USERS = List.of(
            Map.of("id", 1, "name", "Alice", "email", "alice@example.com"),
            Map.of("id", 2, "name", "Bob", "email", "bob@example.com"),
            Map.of("id", 3, "name", "Charlie", "email", "charlie@example.com")
    );

    private final DbLockService dbLockService;
    private final CpuLoadService cpuLoadService;

    public UserController(DbLockService dbLockService, CpuLoadService cpuLoadService) {
        this.dbLockService = dbLockService;
        this.cpuLoadService = cpuLoadService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return USERS;
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
        return USERS.stream()
                .filter(u -> u.get("id").equals(id))
                .findFirst()
                .orElse(Map.of("error", "not found"));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> user) {
        return Map.of(
                "id", 999,
                "name", user.getOrDefault("name", "unknown"),
                "created", true
        );
    }
}
