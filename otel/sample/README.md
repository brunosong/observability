# otel/sample — 3단계: starter 적용 소비 서비스

[`../starter`](../starter) 를 **한 줄 의존**으로 가져다 쓰는 가장 얇은 OTel 소비 서비스 예시입니다. 자체 비즈니스 컨트롤러 (`/hello`) 하나만 가지고, OTel 설정/exporter/공통 resource attribute 는 starter 가 전부 끼워 넣습니다.

## 실행

```bash
../../gradlew :sample:bootRun
# 또는 루트에서
../../gradlew :otel:sample:bootRun
```

기본 포트 `8094`, 기본 프로파일 `dev`. OTLP collector 가 `localhost:4317` 에 떠 있어야 export 가 성공합니다 (`OTEL_EXPORTER_OTLP_ENDPOINT` 로 변경 가능).

| URL | 설명 |
|---|---|
| `http://localhost:8094/hello` | 자체 컨트롤러. 내부에서 `@WithSpan("greet")` 으로 sub-span 생성 |
| `http://localhost:8094/actuator/health` | liveness/readiness probes 포함 (k8s 용) |

메트릭/트레이스/로그는 HTTP 엔드포인트가 아니라 **OTLP 로 collector 에 push** 됩니다 (`/actuator/prometheus` 같은 풀 엔드포인트는 prod 프로파일에선 노출하지 않음).

## `@WithSpan` 데모

```java
@GetMapping("/hello")
public String hello() {
    return greet("world");
}

@WithSpan("greet")
String greet(String name) {
    return "hello, " + name + " (from otel-sample-service)";
}
```

`/hello` 호출 시 collector 의 trace 에 `GET /hello` (HTTP 자동 계측) → `greet` (annotation span) 의 부모-자식 관계가 보입니다.

## 프로파일 전환

```bash
SPRING_PROFILES_ACTIVE=prod ../../gradlew :sample:bootRun
SPRING_PROFILES_ACTIVE=stg  ../../gradlew :sample:bootRun
```

| 프로파일 | trace 샘플링 | metrics 주기 | actuator 노출 |
|---|---|---|---|
| `dev` | 100% | 10s | `*` |
| `stg` | 25% | 30s | `health, info, env, beans, threaddump` |
| `prod` (base) | 5% | 60s | `health, info` |

자세한 차이는 [`../starter/README.md`](../starter/README.md) 참고.

## 설정 (`application.yml`)

```yaml
spring:
  application:
    name: otel-sample-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import: "classpath:application-observability-otel.yml"

server:
  port: 8094
```

## 의존

- `project(':starter')` — OTel SDK / starter / exporter / actuator 가 transitive 로 따라옴
- `spring-boot-starter-web`
