# loadgen — 부하 생성기

스택의 메트릭/트레이스가 비어 보이지 않도록 [`../actuator/demo`](../actuator/demo) (또는 호환 더미 API 가 떠 있는 서비스) 의 엔드포인트를 **주기적으로 두드리는** 독립 Spring Boot 애플리케이션입니다.

루트 [`../README.md`](../README.md) 의 composite build 에 `includeBuild 'loadgen'` 으로 묶여 있지만, 어느 스택(`actuator`/`otel`)에도 속하지 않습니다.

## 실행

```bash
../gradlew :loadgen:bootRun
# 또는 이 디렉터리에서
../gradlew bootRun
```

기본 포트 `8082`. 자체 HTTP API 는 없고 `@Scheduled` 작업만 돕니다.

## 동작

`LoadGenerator` 가 여러 엔드포인트를 각자 다른 주기로 호출하고, 10초마다 누적 통계를 로그로 찍습니다:

```
[STATS] total=1842, failures=12
```

| 메서드 | 대상 | 기본 주기 | property |
|---|---|---|---|
| GET | `/api/hello` | 500ms | `loadgen.interval.hello` |
| GET | `/api/users` | 800ms | `loadgen.interval.users` |
| GET | `/api/users/{id}` (id 1–3 랜덤) | 1000ms | `loadgen.interval.userById` |
| POST | `/api/users` (랜덤 body) | 2000ms | `loadgen.interval.userCreate` |
| GET | `/api/orders` | 1200ms | `loadgen.interval.orders` |
| GET | `/api/products` | 700ms | `loadgen.interval.products` |
| GET | `/api/products/slow` (300–800ms 지연) | 3000ms | `loadgen.interval.slow` |
| GET | `/api/products/flaky` (25% 500) | 600ms | `loadgen.interval.flaky` |

## 설정 (`application.yaml`)

```yaml
loadgen:
  target-url: http://localhost:8081   # 기본 타깃: actuator/demo
  interval:
    hello: 500
    users: 800
    # ...
```

`loadgen.target-url` 만 바꾸면 다른 더미 API 서비스로 향하게 할 수 있습니다 (단, 호출 경로는 위 표와 같이 고정).

### RestTemplate

`RestClientConfig` 에서 `rootUri = loadgen.target-url`, connect timeout 2s / read timeout 5s 로 한 번만 만듭니다.

## 컨테이너

```bash
../gradlew :loadgen:bootJar
docker build -t loadgen:local .
docker run --rm -e LOADGEN_TARGET_URL=http://host.docker.internal:8081 -p 8082:8082 loadgen:local
```

(이미지에는 fat-jar 하나만 들어가는 최소 구성 — [`Dockerfile`](Dockerfile) 참고.)

## 의존

- Spring Boot 2.7.18 (`spring-boot-starter`, `spring-boot-starter-web`)
- Lombok
