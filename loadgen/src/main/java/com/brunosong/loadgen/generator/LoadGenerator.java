package com.brunosong.loadgen.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadGenerator {

    private final RestTemplate restTemplate;
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();

    @Scheduled(fixedRateString = "${loadgen.interval.hello:500}")
    public void hitHello() {
        call("GET /api/hello", () -> restTemplate.getForObject("/api/hello", String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.users:800}")
    public void hitUsersList() {
        call("GET /api/users", () -> restTemplate.getForObject("/api/users", String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.userById:1000}")
    public void hitUserById() {
        int id = ThreadLocalRandom.current().nextInt(1, 4);
        call("GET /api/users/" + id, () -> restTemplate.getForObject("/api/users/{id}", String.class, id));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.userCreate:2000}")
    public void hitUserCreate() {
        Map<String, Object> body = Map.of("name", "user-" + ThreadLocalRandom.current().nextInt(1000));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        call("POST /api/users", () -> restTemplate.exchange("/api/users", HttpMethod.POST, entity, String.class).getBody());
    }

    @Scheduled(fixedRateString = "${loadgen.interval.orders:1200}")
    public void hitOrders() {
        call("GET /api/orders", () -> restTemplate.getForObject("/api/orders", String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.products:700}")
    public void hitProducts() {
        call("GET /api/products", () -> restTemplate.getForObject("/api/products", String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.slow:3000}")
    public void hitSlow() {
        call("GET /api/products/slow", () -> restTemplate.getForObject("/api/products/slow", String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.flaky:600}")
    public void hitFlaky() {
        call("GET /api/products/flaky", () -> restTemplate.getForObject("/api/products/flaky", String.class));
    }

    @Scheduled(fixedRate = 10_000)
    public void reportStats() {
        log.info("[STATS] total={}, failures={}", totalCalls.get(), totalFailures.get());
    }

    private void call(String label, RequestRunnable runnable) {
        long start = System.currentTimeMillis();
        totalCalls.incrementAndGet();
        try {
            runnable.run();
            log.debug("{} OK ({}ms)", label, System.currentTimeMillis() - start);
        } catch (RestClientException e) {
            totalFailures.incrementAndGet();
            log.warn("{} FAIL: {}", label, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface RequestRunnable {
        Object run() throws RestClientException;
    }
}
