# otel — OpenTelemetry 스택

OpenTelemetry SDK + Spring Boot Starter + OTLP exporter 조합을 정리한 **독립 Gradle build** 입니다. 루트 [`../README.md`](../README.md) 의 composite build 에서 `includeBuild 'otel'` 로 묶입니다.

**메트릭/트레이스/로그를 한 채널(OTLP)로 통합**해 collector 로 push 하는 방식 — 트레이싱이 필요하거나 풀(pull) 방식 Prometheus 스크랩을 벗어나려는 경우에 씁니다. Prometheus 만 있고 메트릭만 모으면 충분하면 [`../actuator/`](../actuator/) 스택을 보세요.

## 모듈 구성 (학습 흐름: `demo → starter → sample`)

| 모듈 | 단계 | 무엇 |
|---|---|---|
| [`demo/`](demo/) | 1단계 — 기본 사용법 | `opentelemetry-spring-boot-starter` 직접 적용, OTLP 로 메트릭/트레이스/로그 전송 (`:8080`) |
| [`starter/`](starter/) | 2단계 — 공통화 | OTel BOM + Spring Boot starter + exporter 묶음, 프로파일별 샘플링/주기 프리셋 |
| [`sample/`](sample/) | 3단계 — 적용 | starter 한 줄 의존만으로 prod-safe 설정을 받는 소비 서비스 (`:8094`, `@WithSpan` 데모) |

## 빌드 / 실행

```bash
# 루트에서 (composite)
../gradlew :otel:build -x test
../gradlew :otel:sample:bootRun

# 이 디렉터리에서 (독립 빌드)
../gradlew build -x test
../gradlew :sample:bootRun
```

### Collector 필요

OTLP 데이터를 받을 collector 가 떠 있어야 합니다 (`localhost:4317` gRPC 기본). 예: `otel-collector`, Jaeger (gRPC OTLP receiver), Tempo.

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://my-collector:4317 ../gradlew :sample:bootRun
```

Collector 가 없으면 export 실패 로그가 찍히지만 애플리케이션은 정상 동작합니다.

## 핵심 의존

- Java 17, Spring Boot 2.7.18
- `opentelemetry-bom` 1.32.0
- `opentelemetry-instrumentation-bom-alpha` 1.32.0-alpha
- `opentelemetry-spring-boot-starter` — auto-instrumentation
- `opentelemetry-exporter-otlp`, `opentelemetry-exporter-logging`

## 디렉터리

```
otel/
├─ settings.gradle      # include 'demo', 'starter', 'sample'
├─ build.gradle         # 공용 plugin/Java toolchain
├─ demo/                # 1단계
├─ starter/             # 2단계
└─ sample/              # 3단계
```
