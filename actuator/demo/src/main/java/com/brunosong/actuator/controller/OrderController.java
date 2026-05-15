package com.brunosong.actuator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", 1001, "userId", 1, "amount", 49.99, "status", "PAID"),
                Map.of("id", 1002, "userId", 2, "amount", 129.50, "status", "PENDING"),
                Map.of("id", 1003, "userId", 1, "amount", 19.95, "status", "SHIPPED")
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
