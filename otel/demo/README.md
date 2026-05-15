# otel/demo — 1단계: OTel Spring Boot Starter 기본 사용

`opentelemetry-spring-boot-starter` 를 직접 끼우고, JPA/Web/HTTP client 자동 계측이 어떻게 트레이스로 잡히는지 확인하는 단계입니다.

학습 흐름상 [`../starter`](../starter) 로 공통화하기 전 단계 — 모든 OTel 설정을 `application.yaml` 에 직접 적어두었습니다.

## 실행

```bash
../../gradlew :demo:bootRun
# 또는 루트에서
../../gradlew :otel:demo:bootRun
```

기본 포트 `8080`. OTLP collector 가 `localhost:4317` 에 떠 있어야 export 가 성공합니다 (`OTEL_EXPORTER_OTLP_ENDPOINT` 로 변경 가능).

## 엔드포인트

| URL | 설명 |
|---|---|
| `http://localhost:8080/api/samples` (`GET`/`POST`) | JPA 통한 CRUD — DB 호출이 자식 span 으로 잡힘 |
| `http://localhost:8080/api/samples/trace-id` | 현재 요청의 `traceId` 반환 (collector 에서 검색용) |
| `http://localhost:8080/actuator/health` | health, prometheus, metrics 노출 |
| `http://localhost:8080/h2-console` | H2 콘솔 (`jdbc:h2:mem:oteldb`) |

## `@WithSpan` 데모

`SampleService` 메서드에 `@WithSpan` 으로 비즈니스 로직 단위 span 을 추가하고, `@SpanAttribute` 로 인자를 span attribute 로 기록합니다.

```java
@WithSpan("SampleService.create")
@Transactional
public Sample create(@SpanAttribute("sample.name") String name) {
    Span.current().setAttribute("sample.name.length", name.length());
    return sampleRepository.save(new Sample(name));
}
```

## 주요 설정 (`application.yaml`)

```yaml
otel:
  resource:
    attributes:
      service.name: ${spring.application.name}
      service.namespace: monitoring
      deployment.environment: ${OTEL_DEPLOYMENT_ENV:dev}
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
      protocol: grpc
  traces:
    sampler: ${OTEL_TRACES_SAMPLER:parentbased_always_on}   # 데모용 100%
  metrics:
    export:
      interval: 30000
  propagators: tracecontext,baggage,b3
```

## 의존

- `opentelemetry-spring-boot-starter` — Web/JPA/HTTP client 자동 계측
- `opentelemetry-instrumentation-annotations` — `@WithSpan`, `@SpanAttribute`
- `opentelemetry-exporter-otlp` (gRPC), `opentelemetry-exporter-logging`
- `spring-boot-starter-web`, `spring-boot-starter-data-jpa` + H2
- `spring-boot-starter-actuator`
- `springdoc-openapi-ui` — `/swagger-ui.html`
