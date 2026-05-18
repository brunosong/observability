package com.brunosong.loadgen.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadGenerator {

    private final RestTemplate restTemplate;
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();

    @Value("${loadgen.scenario.poolExhaustConcurrency:14}")
    private int poolExhaustConcurrency;

    @Value("${loadgen.scenario.poolExhaustHoldMinMs:1000}")
    private long poolExhaustHoldMinMs;

    @Value("${loadgen.scenario.poolExhaustHoldMaxMs:10000}")
    private long poolExhaustHoldMaxMs;

    @Value("${loadgen.scenario.cpuExhaustConcurrency:4}")
    private int cpuExhaustConcurrency;

    @Value("${loadgen.scenario.cpuExhaustDurationMinMs:1000}")
    private long cpuExhaustDurationMinMs;

    @Value("${loadgen.scenario.cpuExhaustDurationMaxMs:10000}")
    private long cpuExhaustDurationMaxMs;

    @Value("${loadgen.scenario.cpuExhaustThreads:4}")
    private int cpuExhaustThreads;

    private ExecutorService burstExecutor;

    @PostConstruct
    void init() {
        this.burstExecutor = Executors.newFixedThreadPool(32);
    }

    @PreDestroy
    void shutdown() {
        if (burstExecutor != null) {
            burstExecutor.shutdownNow();
        }
    }

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

    /**
     * Pool exhaustion: fire N concurrent /api/admin/hold-conn requests.
     * Each request holds a connection for a RANDOM duration in [minMs, maxMs] →
     * connections release at staggered times, mimicking realistic traffic.
     * With Hikari pool=10, N>10 forces active=10 + pending>0 for the duration.
     */
    @Scheduled(fixedRateString = "${loadgen.scenario.poolExhaustIntervalMs:300000}",
            initialDelayString = "${loadgen.scenario.poolExhaustInitialDelayMs:30000}")
    public void scenarioPoolExhaust() {
        log.warn("[SCENARIO][POOL-EXHAUST] firing {} concurrent /api/admin/hold-conn (hold=random[{}..{}]ms) — pool should saturate",
                poolExhaustConcurrency, poolExhaustHoldMinMs, poolExhaustHoldMaxMs);
        for (int i = 0; i < poolExhaustConcurrency; i++) {
            long holdMs = ThreadLocalRandom.current().nextLong(poolExhaustHoldMinMs, poolExhaustHoldMaxMs + 1);
            String path = "/api/admin/hold-conn?ms=" + holdMs;
            burstExecutor.submit(() ->
                    call("SCENARIO GET " + path, () -> restTemplate.getForObject(path, String.class)));
        }
    }

    /**
     * CPU exhaustion: fire N concurrent /api/orders/cpu-spike each burning K threads.
     * Each request burns for a RANDOM duration in [minMs, maxMs].
     */
    @Scheduled(fixedRateString = "${loadgen.scenario.cpuExhaustIntervalMs:420000}",
            initialDelayString = "${loadgen.scenario.cpuExhaustInitialDelayMs:120000}")
    public void scenarioCpuExhaust() {
        log.warn("[SCENARIO][CPU-EXHAUST] firing {} concurrent /api/orders/cpu-spike (duration=random[{}..{}]ms, {} threads each) — CPU should peak",
                cpuExhaustConcurrency, cpuExhaustDurationMinMs, cpuExhaustDurationMaxMs, cpuExhaustThreads);
        for (int i = 0; i < cpuExhaustConcurrency; i++) {
            long durationMs = ThreadLocalRandom.current().nextLong(cpuExhaustDurationMinMs, cpuExhaustDurationMaxMs + 1);
            String path = "/api/orders/cpu-spike?durationMs=" + durationMs + "&threads=" + cpuExhaustThreads;
            burstExecutor.submit(() ->
                    call("SCENARIO GET " + path, () -> restTemplate.getForObject(path, String.class)));
        }
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
            long took = System.currentTimeMillis() - start;
            if (took > 1000) {
                log.info("{} OK ({}ms)", label, took);
            } else {
                log.debug("{} OK ({}ms)", label, took);
            }
        } catch (RestClientException e) {
            totalFailures.incrementAndGet();
            log.warn("{} FAIL ({}ms): {}", label, System.currentTimeMillis() - start, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface RequestRunnable {
        Object run() throws RestClientException;
    }
}
