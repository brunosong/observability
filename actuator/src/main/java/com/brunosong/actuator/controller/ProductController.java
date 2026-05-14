package com.brunosong.actuator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/products")
public class ProductController {

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
}
