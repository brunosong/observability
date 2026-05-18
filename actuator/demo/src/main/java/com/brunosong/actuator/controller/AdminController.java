package com.brunosong.actuator.controller;

import com.brunosong.actuator.service.ConnectionHoldService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ConnectionHoldService connectionHoldService;

    public AdminController(ConnectionHoldService connectionHoldService) {
        this.connectionHoldService = connectionHoldService;
    }

    @GetMapping("/hold-conn")
    public Map<String, Object> holdConn(@RequestParam(defaultValue = "20000") long ms) throws InterruptedException {
        long start = System.currentTimeMillis();
        long ping = connectionHoldService.holdConnection(ms);
        return Map.of(
                "endpoint", "hold-conn",
                "ping", ping,
                "holdMs", ms,
                "tookMs", System.currentTimeMillis() - start
        );
    }
}
