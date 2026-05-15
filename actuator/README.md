# actuator — Actuator + Micrometer + Prometheus 스택

Spring Boot Actuator + Micrometer + `micrometer-registry-prometheus` 조합을 학습용으로 정리한 **독립 Gradle build** 입니다. 루트 [`../README.md`](../README.md) 의 composite build 에서 `includeBuild 'actuator'` 로 묶입니다.

Prometheus 가 이미 있고 **pull 방식 스크랩으로 메트릭만** 수집하면 충분한 환경을 가정합니다. 트레이싱이나 OTLP 통합이 필요하면 [`../otel/`](../otel/) 스택을 보세요.

## 모듈 구성 (학습 흐름: `demo → starter → sample`)

| 모듈 | 단계 | 무엇 |
|---|---|---|
| [`demo/`](demo/) | 1단계 — 기본 사용법 | Actuator 의존 한 줄로 모든 엔드포인트 노출, Prometheus 메트릭 풀 스크랩 (`:8081`) |
| [`starter/`](starter/) | 2단계 — 공통화 | `MeterRegistryCustomizer` 로 공통 태그 자동 부착 + prod/dev/stg 프로파일별 노출 프리셋 |
| [`sample/`](sample/) | 3단계 — 적용 | starter 한 줄 의존만으로 prod-safe 기본값을 받는 소비 서비스 (`:8090`) |

## 빌드 / 실행

```bash
# 루트에서 (composite)
../gradlew :actuator:build -x test
../gradlew :actuator:sample:bootRun

# 이 디렉터리에서 (독립 빌드)
../gradlew build -x test
../gradlew :sample:bootRun
```

## 핵심 의존 (root `build.gradle`)

- Java 17, Spring Boot 2.7.18
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`

세부 의존은 각 sub-module 의 `build.gradle` 참고.

## 디렉터리

```
actuator/
├─ settings.gradle      # include 'demo', 'starter', 'sample'
├─ build.gradle         # 공용 plugin/Java toolchain
├─ demo/                # 1단계
├─ starter/             # 2단계
└─ sample/              # 3단계
```
