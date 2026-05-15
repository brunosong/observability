package com.brunosong.actuator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final List<Map<String, Object>> USERS = List.of(
            Map.of("id", 1, "name", "Alice", "email", "alice@example.com"),
            Map.of("id", 2, "name", "Bob", "email", "bob@example.com"),
            Map.of("id", 3, "name", "Charlie", "email", "charlie@example.com")
    );

    @GetMapping
    public List<Map<String, Object>> list() {
        return USERS;
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
