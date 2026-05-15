# actuator/starter — 2단계: 공용 Observability Starter

Actuator + Micrometer + Prometheus 사용 시 모든 서비스가 반복하던 설정을 **한 줄 의존**으로 끝내기 위한 라이브러리입니다. Spring Boot 2.7 의 `AutoConfiguration` 으로 등록됩니다.

소비 측 사용 예시는 [`../sample`](../sample) 참고.

## 제공하는 것

1. **`MeterRegistryCustomizer<MeterRegistry>`**
   모든 메트릭에 공통 태그 자동 부착:
   - `application` ← `spring.application.name`
   - `namespace` ← `common.observability.namespace` (기본 `monitoring`)
   - `env` ← `common.observability.environment` (프로파일별 기본값)
2. **프로파일별 노출 프리셋** — base + dev + stg 오버라이드:
   | 파일 | 노출 엔드포인트 | health detail |
   |---|---|---|
   | `application-observability.yml` (base, prod) | `health, info, prometheus` | `when-authorized` |
   | `application-observability-dev.yml` | `*` (전체) | `always` |
   | `application-observability-stg.yml` | `health, info, prometheus, metrics, env, beans, threaddump` | `when-authorized` |

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
    import: "classpath:application-observability.yml"
```

`config.import` 만 하면 base 파일이 들어오고, `spring.profiles.active` 에 따라 `-dev` / `-stg` 가 자동으로 덮어씁니다.

## Properties

| Property | Default | 용도 |
|---|---|---|
| `common.observability.namespace` | `monitoring` | 메트릭 태그 `namespace` |
| `common.observability.environment` | `prod` (base) / `dev` / `stg` (프로파일) | 메트릭 태그 `env` |

환경변수 `DEPLOY_ENV` 로 `environment` 만 별도 오버라이드 가능.

## 빌드 / 구조

```bash
../../gradlew :starter:build
```

```
starter/
└─ src/main/
   ├─ java/com/brunosong/observability/
   │  ├─ ObservabilityAutoConfiguration.java     # @AutoConfiguration + @ConditionalOnClass(MeterRegistry)
   │  └─ ObservabilityProperties.java            # @ConfigurationProperties("common.observability")
   └─ resources/
      ├─ application-observability.yml           # prod-safe base
      ├─ application-observability-dev.yml
      ├─ application-observability-stg.yml
      └─ META-INF/spring/
         org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 의존

- `java-library` 플러그인 — 실행 가능한 boot jar 가 아니라 라이브러리로 publish
- `api 'spring-boot-starter-actuator'` / `api 'micrometer-registry-prometheus'`
  → 소비 측에서 별도 의존 추가 불필요
- `spring-boot-configuration-processor` (annotationProcessor) — IDE 자동완성용
