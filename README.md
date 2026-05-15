# monitoring — Observability Sample Project

Spring Boot 기반 애플리케이션의 **observability(관측 가능성)** 를 두 가지 스택으로 실습하는 샘플 프로젝트입니다. 각 스택(`actuator/`, `otel/`)을 **독립된 Gradle build** 로 분리하고, 루트에서 **Gradle composite build** 로 묶어 한 곳에서 빌드할 수 있게 했습니다.

## 구조

```
monitoring/
├─ settings.gradle              # umbrella: includeBuild 'actuator', 'otel', 'loadgen'
├─ gradlew, gradle/wrapper/     # 공용 wrapper
├─ README.md, docs/, k8s/
│
├─ actuator/                    # Actuator + Micrometer + Prometheus 스택 (독립 Gradle build)
│  ├─ settings.gradle
│  ├─ build.gradle
│  ├─ demo/                     # 1단계 — 기본 Actuator 사용법
│  ├─ starter/                  # 2단계 — 공용 starter 라이브러리로 추상화
│  └─ sample/                   # 3단계 — starter 를 한 줄 의존으로 적용한 소비 서비스
│
├─ otel/                        # OpenTelemetry 스택 (독립 Gradle build)
│  ├─ settings.gradle
│  ├─ build.gradle
│  ├─ demo/                     # 1단계 — OTel Spring Boot Starter 기본 사용
│  ├─ starter/                  # 2단계 — 공용 OTel starter (메트릭/트레이스/로그 통합)
│  └─ sample/                   # 3단계 — starter 적용한 소비 서비스 (@WithSpan 데모)
│
└─ loadgen/                     # 독립 부하 생성기 (어느 스택에도 속하지 않음)
   ├─ settings.gradle
   └─ build.gradle
```

각 스택 폴더 안의 `demo → starter → sample` 흐름이 **"기본 사용 → 공통화 → 적용"** 학습 시나리오 그대로입니다.

## 어느 스택을 쓸까

| 상황 | 선택 |
|---|---|
| Prometheus 가 이미 깔려있고, 풀(pull) 방식 스크랩으로 메트릭만 모으면 충분 | `actuator/` |
| 트레이싱이 필요하거나, 메트릭/트레이스/로그를 한 채널(OTLP)로 통합하고 싶음 | `otel/` |
| 둘 다 쓰고 싶음 | **권장 안 함** — 메트릭이 두 경로로 중복 수집되어 비용/혼선 ↑ |

## Quick Start

### 빌드

```bash
# 전체 빌드 (composite — 모든 sub-build 동시)
./gradlew build -x test

# 한 스택만
cd actuator && ../gradlew build -x test
cd otel && ../gradlew build -x test

# 루트에서 특정 sub-build 의 특정 모듈만
./gradlew :actuator:sample:build
./gradlew :otel:sample:build
```

### Actuator 스택 샘플 실행

```bash
./gradlew :actuator:sample:bootRun
```

| URL | 설명 |
|---|---|
| `http://localhost:8090/hello` | 데모 컨트롤러 |
| `http://localhost:8090/actuator` | 노출 엔드포인트 목록 |
| `http://localhost:8090/actuator/health` | Health (liveness/readiness probes 포함) |
| `http://localhost:8090/actuator/prometheus` | Prometheus 스크랩 메트릭 (공통 태그 자동 부착) |

### OTel 스택 샘플 실행

OTel collector 가 필요합니다(예: 로컬 `otel-collector`, Tempo, Jaeger). 기본 endpoint 는 `http://localhost:4317`. `OTEL_EXPORTER_OTLP_ENDPOINT` 환경변수로 변경.

```bash
./gradlew :otel:sample:bootRun
```

| URL | 설명 |
|---|---|
| `http://localhost:8094/hello` | 데모 컨트롤러 (`@WithSpan` 으로 sub-span 생성) |
| `http://localhost:8094/actuator/health` | Health (k8s probes 용) |

메트릭/트레이스/로그는 HTTP 가 아니라 OTLP 로 collector 에 push 됩니다.

### 프로파일

두 sample 모두 기본 `dev` 프로파일로 뜹니다. 환경 전환:

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew :actuator:sample:bootRun
SPRING_PROFILES_ACTIVE=stg  ./gradlew :otel:sample:bootRun
```

## starter 사용법

### actuator 스택 (Prometheus 경로)

**`build.gradle`**
```gradle
dependencies {
    implementation project(':starter')        // 같은 sub-build 내부
    // 또는 외부 consumer 라면: implementation 'com.brunosong:starter'
}
```

**`application.yml`**
```yaml
spring:
  application:
    name: your-service-name
  config:
    import: "classpath:application-observability.yml"
```

`actuator/starter/` 가 제공하는 것:

- `MeterRegistryCustomizer` — 모든 메트릭에 `application`, `namespace`, `env` 공통 태그 자동 부착
- `application-observability.yml` — prod-safe 기본값 (`health, info, prometheus` 만 노출)
- `application-observability-dev.yml` — dev 오버라이드 (전체 노출, `show-details: always`)
- `application-observability-stg.yml` — stg 오버라이드

### otel 스택

**`build.gradle`**
```gradle
dependencies {
    implementation project(':starter')
}
```

**`application.yml`**
```yaml
spring:
  application:
    name: your-service-name
  config:
    import: "classpath:application-observability-otel.yml"
```

`otel/starter/` 가 제공하는 것:

- OTel SDK + OTLP exporter + Spring Boot instrumentation 일괄 제공
- Resource attribute 표준화 — `service.name` ← `spring.application.name`, `service.namespace`, `deployment.environment`
- `application-observability-otel.yml` — prod-safe (Actuator 는 `health, info` 만, trace 샘플링 5%, 메트릭 export 60s)
- `application-observability-otel-dev.yml` — dev 오버라이드 (전체 actuator 노출, 샘플링 100%, 메트릭 10s)
- `application-observability-otel-stg.yml` — stg 오버라이드 (샘플링 25%, 메트릭 30s)

### Properties (양 starter 공통)

| Property | Default | 설명 |
|---|---|---|
| `common.observability.namespace` | `monitoring` | 메트릭 태그 / OTel `service.namespace` |
| `common.observability.environment` | `prod` (base), `dev`/`stg` (프로파일별) | 메트릭 태그 `env` / OTel `deployment.environment` |

### OTel 환경변수

| Env | Default | 설명 |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector 주소 (gRPC) |
| `OTEL_TRACES_SAMPLER` | `parentbased_traceidratio` | 샘플러 종류 |
| `OTEL_TRACES_SAMPLER_ARG` | `0.05` (prod) | 샘플링 비율 |
| `DEPLOY_ENV` | (프로파일에 따라) | `deployment.environment` 오버라이드 |

## Composite build 동작

루트 `settings.gradle` 에 `includeBuild` 로 세 sub-build 를 묶었습니다.

```gradle
rootProject.name = 'monitoring'

includeBuild 'actuator'
includeBuild 'otel'
includeBuild 'loadgen'
```

- 각 sub-build 는 자기 자신만으로도 빌드 가능 (`cd actuator && ../gradlew build`)
- 루트에서 빌드하면 세 build 가 한 번에 조립됨
- 외부 레포에서 starter 만 가져다 쓸 수 있도록 진짜 라이브러리로 publish 도 각자 가능
- starter 가 외부 의존 좌표로 적혀 있어도, composite 컨텍스트에선 로컬 소스로 자동 substitute 됨

## Tech Stack

- Java 17
- Spring Boot 2.7.18
- Micrometer + `micrometer-registry-prometheus`
- OpenTelemetry SDK 1.32.0 / Instrumentation 1.32.0-alpha
- Prometheus + Grafana (k8s 스택, [`k8s/`](k8s/) 참고)
- Gradle composite build
