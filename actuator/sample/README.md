# actuator/sample — 3단계: starter 적용 소비 서비스

[`../starter`](../starter) 를 **한 줄 의존**으로 가져다 쓰는 가장 얇은 소비 서비스 예시입니다. 자기 자신의 비즈니스 컨트롤러 (`/hello`) 하나만 정의하고, observability 설정은 starter 가 전부 끼워 넣습니다.

## 실행

```bash
../../gradlew :sample:bootRun
# 또는 루트에서
../../gradlew :actuator:sample:bootRun
```

기본 포트 `8090`, 기본 프로파일 `dev` (`SPRING_PROFILES_ACTIVE` 로 오버라이드).

| URL | 설명 |
|---|---|
| `http://localhost:8090/hello` | 자체 비즈니스 컨트롤러 |
| `http://localhost:8090/actuator` | starter 가 노출한 엔드포인트 (프로파일별 다름) |
| `http://localhost:8090/actuator/health` | liveness/readiness probes 포함 |
| `http://localhost:8090/actuator/prometheus` | 공통 태그 (`application=sample-service, namespace=monitoring, env=dev`) 자동 부착 |

## 프로파일 전환

```bash
SPRING_PROFILES_ACTIVE=prod ../../gradlew :sample:bootRun
SPRING_PROFILES_ACTIVE=stg  ../../gradlew :sample:bootRun
```

- `dev` — Actuator 전체 노출, `show-details: always`
- `stg` — 디버깅에 필요한 엔드포인트만, `when-authorized`
- `prod` — `health, info, prometheus` 만

자세한 차이는 [`../starter/README.md`](../starter/README.md) 참고.

## 설정 (`application.yml`)

```yaml
spring:
  application:
    name: sample-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import: "classpath:application-observability.yml"

server:
  port: 8090
```

## 의존

- `project(':starter')` — `actuator-starter` + `prometheus registry` 가 transitive 로 따라옴
- `spring-boot-starter-web`
