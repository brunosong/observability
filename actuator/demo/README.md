# actuator/demo — 1단계: Actuator 기본 사용

Actuator 의존을 추가하고 모든 엔드포인트를 열어 **Prometheus 스크랩 풀 경로**가 어떻게 동작하는지 보는 가장 단순한 형태의 데모입니다.

학습 흐름상 [`../starter`](../starter) 로 공통화하기 전 단계 — 모든 설정을 `application.yaml` 에 직접 적어두었습니다.

## 실행

```bash
../../gradlew :demo:bootRun
# 또는 루트에서
../../gradlew :actuator:demo:bootRun
```

기본 포트 `8081`.

## 노출 엔드포인트

| URL | 설명 |
|---|---|
| `http://localhost:8081/actuator` | 노출 엔드포인트 목록 (`include: "*"`) |
| `http://localhost:8081/actuator/health` | `show-details: always` |
| `http://localhost:8081/actuator/prometheus` | Prometheus 스크랩 메트릭 (`application` 공통 태그 부착) |
| `http://localhost:8081/h2-console` | H2 콘솔 (`jdbc:h2:mem:actuatordb`) |

## 부하 생성용 더미 API

`com.brunosong.actuator.controller` 패키지에 메트릭 관찰용 dummy 컨트롤러가 있습니다 — [`../../loadgen`](../../loadgen) 이 이 엔드포인트들을 주기적으로 호출합니다.

| URL | 동작 |
|---|---|
| `GET /api/hello`, `GET /api/hello/{name}` | 단순 응답 |
| `GET /api/users`, `GET /api/users/{id}`, `POST /api/users` | 사용자 목록/조회/생성 |
| `GET /api/orders`, `GET /api/orders/{id}` | 주문 목록/조회 |
| `GET /api/products` | 상품 목록 |
| `GET /api/products/slow` | 300–800ms 랜덤 지연 (latency 히스토그램 관찰용) |
| `GET /api/products/flaky` | 25% 확률 500 (`http_server_requests_seconds_count{status="500"}` 관찰용) |

## 주요 설정 (`application.yaml`)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"          # 데모용 — 운영에서는 starter 의 prod 프리셋 사용
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
  prometheus:
    metrics:
      export:
        enabled: true
```

## 의존

- `spring-boot-starter-web`, `spring-boot-starter-actuator`
- `spring-boot-starter-data-jpa` + H2 (런타임)
- `micrometer-registry-prometheus` (런타임)
