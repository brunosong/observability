# otel/starter — 2단계: 공용 OTel Starter

OpenTelemetry SDK + Spring Boot 자동 계측 + OTLP exporter + Actuator 까지 묶어 **한 줄 의존**으로 끝내기 위한 라이브러리입니다. Spring Boot 2.7 의 `AutoConfiguration` 으로 등록됩니다.

소비 측 사용 예시는 [`../sample`](../sample) 참고.

## 제공하는 것

1. **의존 묶음** (`api` 로 expose → 소비 측에서 별도 추가 불필요)
   - `opentelemetry-bom` 1.32.0, `opentelemetry-instrumentation-bom-alpha` 1.32.0-alpha
   - `opentelemetry-spring-boot-starter` — Web/JPA/HTTP client 자동 계측
   - `opentelemetry-instrumentation-annotations` — `@WithSpan`, `@SpanAttribute`
   - `opentelemetry-exporter-otlp` (gRPC), `opentelemetry-exporter-logging`
   - `spring-boot-starter-actuator`
2. **Resource attribute 표준화** — `service.name` ← `spring.application.name`, `service.namespace` ← `common.observability.namespace`, `deployment.environment` ← `common.observability.environment`
3. **프로파일별 샘플링/주기 프리셋** — base + dev + stg:
   | 파일 | trace sampler | metrics interval | actuator 노출 |
   |---|---|---|---|
   | `application-observability-otel.yml` (base, prod) | `parentbased_traceidratio` 5% | 60s | `health, info` |
   | `application-observability-otel-dev.yml` | `always_on` (100%) | 10s | `*` |
   | `application-observability-otel-stg.yml` | `parentbased_traceidratio` 25% | 30s | `health, info, env, beans, threaddump` |

## 사용법

**`build.gradle`**
```gradle
dependencies {
    implementation project(':starter')        // 같은 sub-build 내부
    // 외부 consumer 라면: implementation 'com.brunosong:starter:0.0.1-SNAPSHOT'
}
```

**`application.yml`**
```yaml
spring:
  application:
    name: your-service-name
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import: "classpath:application-observability-otel.yml"
```

`config.import` 만 하면 base 파일이 들어오고, `spring.profiles.active` 에 따라 `-dev` / `-stg` 가 자동으로 덮어씁니다.

## Properties

| Property | Default | 용도 |
|---|---|---|
| `common.observability.namespace` | `monitoring` | OTel resource `service.namespace` |
| `common.observability.environment` | `prod` (base) / `dev` / `stg` (프로파일) | OTel resource `deployment.environment` |

## 환경변수 (OTel SDK)

| Env | Default | 설명 |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector 주소 (gRPC) |
| `OTEL_TRACES_EXPORTER` | `otlp` | trace exporter (`none` 으로 끄기 가능) |
| `OTEL_TRACES_SAMPLER` | `parentbased_traceidratio` | 샘플러 |
| `OTEL_TRACES_SAMPLER_ARG` | `0.05` (prod) | 샘플링 비율 |
| `OTEL_METRICS_EXPORTER` | `otlp` | metrics exporter |
| `OTEL_LOGS_EXPORTER` | `otlp` | logs exporter |
| `DEPLOY_ENV` | (프로파일에 따라) | `deployment.environment` 만 별도 오버라이드 |

## 빌드 / 구조

```bash
../../gradlew :starter:build
```

```
starter/
└─ src/main/
   ├─ java/com/brunosong/observability/otel/
   │  ├─ ObservabilityOtelAutoConfiguration.java   # @AutoConfiguration + @ConditionalOnClass(OpenTelemetry)
   │  └─ ObservabilityOtelProperties.java          # @ConfigurationProperties("common.observability")
   └─ resources/
      ├─ application-observability-otel.yml        # prod-safe base
      ├─ application-observability-otel-dev.yml
      ├─ application-observability-otel-stg.yml
      └─ META-INF/spring/
         org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

자동 설정 클래스 자체는 비어 있고, **유효한 부분은 yml + transitive 의존** 입니다 — Spring Boot OTel starter 가 application property 만으로 모든 컴포넌트를 조립하기 때문.
