package com.brunosong.loadgen.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadGenerator {

    private final RestClient restClient;
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();

    @Scheduled(fixedRateString = "${loadgen.interval.hello:500}")
    public void hitHello() {
        call("GET /api/hello", () -> restClient.get().uri("/api/hello").retrieve().body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.users:800}")
    public void hitUsersList() {
        call("GET /api/users", () -> restClient.get().uri("/api/users").retrieve().body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.userById:1000}")
    public void hitUserById() {
        int id = ThreadLocalRandom.current().nextInt(1, 4);
        call("GET /api/users/" + id, () -> restClient.get().uri("/api/users/{id}", id).retrieve().body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.userCreate:2000}")
    public void hitUserCreate() {
        Map<String, Object> body = Map.of("name", "user-" + ThreadLocalRandom.current().nextInt(1000));
        call("POST /api/users", () -> restClient.post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.orders:1200}")
    public void hitOrders() {
        call("GET /api/orders", () -> restClient.get().uri("/api/orders").retrieve().body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.products:700}")
    public void hitProducts() {
        call("GET /api/products", () -> restClient.get().uri("/api/products").retrieve().body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.slow:3000}")
    public void hitSlow() {
        call("GET /api/products/slow", () -> restClient.get().uri("/api/products/slow").retrieve().body(String.class));
    }

    @Scheduled(fixedRateString = "${loadgen.interval.flaky:600}")
    public void hitFlaky() {
        call("GET /api/products/flaky", () -> restClient.get().uri("/api/products/flaky").retrieve().body(String.class));
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
