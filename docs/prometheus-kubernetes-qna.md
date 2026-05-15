# Prometheus + Kubernetes 학습 노트

이 프로젝트를 셋업하면서 Prometheus와 Kubernetes의 동작 원리에 대해 정리한 Q&A. 셋업 절차 자체는 git history와 `k8s/README.md`를 참고.

## 목차

1. [Prometheus에서 Pod의 actuator 엔드포인트는 어떻게 scrape하나](#1-prometheus에서-pod의-actuator-엔드포인트는-어떻게-scrape하나)
2. [Prometheus는 어느 네임스페이스에 두고, 크로스 네임스페이스 모니터링은 가능한가](#2-prometheus는-어느-네임스페이스에-두고-크로스-네임스페이스-모니터링은-가능한가)
3. [모든 트래픽이 Service를 거치는데 어떻게 Pod별로 메트릭을 수집하나](#3-모든-트래픽이-service를-거치는데-어떻게-pod별로-메트릭을-수집하나)
4. [Prometheus가 클러스터 밖에 있으면 위 구조가 불가능한가](#4-prometheus가-클러스터-밖에-있으면-위-구조가-불가능한가)
5. [ServiceMonitor 자세히](#5-servicemonitor-자세히)
6. [ServiceMonitor는 능동적으로 뭔가 하는 객체인가](#6-servicemonitor는-능동적으로-뭔가-하는-객체인가)
7. [Prometheus Operator는 뭐고, 필수인가](#7-prometheus-operator는-뭐고-필수인가)
8. [CR, CRD는 무엇인가](#8-cr-crd는-무엇인가)
9. [Operator와 Prometheus는 어떻게 통신하나](#9-operator와-prometheus는-어떻게-통신하나)

---

## 1. Prometheus에서 Pod의 actuator 엔드포인트는 어떻게 scrape하나

### 일반 Prometheus vs Operator 방식

| 구분 | 일반 Prometheus | Prometheus Operator |
|------|----------------|---------------------|
| scrape 설정 | `prometheus.yml`의 `scrape_configs` 직접 작성 | **ServiceMonitor** / **PodMonitor** CR 적용 |
| reload | ConfigMap reload 또는 재시작 | Operator가 자동 reconcile |
| annotation 기반 (`prometheus.io/scrape: true`) | 일반적으로 사용 | **기본 미지원** (additionalScrapeConfigs로 추가해야 함) |

### ServiceMonitor 흐름

```
[Spring Boot Pod]
  /actuator/prometheus 엔드포인트 노출
        ↓ (selector로 매칭)
[Service]
  label: app=actuator, port name=http
        ↓ (selector로 매칭)
[ServiceMonitor CR]
  앱별 scrape 설정 (경로, 주기, relabel 등)
        ↓ (Operator가 watch)
[Prometheus 서버]
  자동으로 scrape config 갱신
```

각 앱마다 **Service + ServiceMonitor 두 개의 YAML**을 작성한다.

### Operator의 ServiceMonitor 발견 규칙

기본값: **같은 release 라벨**(예: `release: prom`)이 붙은 ServiceMonitor만 발견.

`kube-prometheus-stack-values.yaml`에 아래 설정으로 풀어둠 → 모든 네임스페이스의 모든 ServiceMonitor 자동 발견:

```yaml
serviceMonitorSelectorNilUsesHelmValues: false
podMonitorSelectorNilUsesHelmValues: false
```

### Spring Boot 쪽에서 필요한 것

1. `micrometer-registry-prometheus` 의존성 추가
2. `application.yaml`에 `management.endpoints.web.exposure.include`에 `prometheus` 포함
3. → `/actuator/prometheus` 엔드포인트에서 Prometheus 포맷 메트릭 노출

---

## 2. Prometheus는 어느 네임스페이스에 두고, 크로스 네임스페이스 모니터링은 가능한가

### 표준 토폴로지

```
┌─ namespace: monitoring ────────────────┐
│  Prometheus, Grafana, Alertmanager,    │
│  Operator, kube-state-metrics 등       │
└────────────────────────────────────────┘
            ↑ scrape (cross-namespace)
┌─ namespace: default / apps / ... ──────┐
│  실제 서비스 Pod + Service             │
│  + ServiceMonitor (앱 옆에 같이 둠)    │
└────────────────────────────────────────┘
```

### 왜 분리하나

| 이유 | 설명 |
|------|------|
| **권한 격리** | RBAC, NetworkPolicy, ResourceQuota를 네임스페이스 단위로 관리 |
| **라이프사이클 분리** | 앱 배포와 무관하게 Prometheus만 업그레이드. 한 번에 stack 정리도 쉬움 |
| **장애 격리** | 앱 네임스페이스 전체 삭제해도 모니터링 살아있어야 사후 분석 가능 |
| **표준 관례** | Helm chart, 튜토리얼, 도구들이 모두 `monitoring` 네임스페이스 가정 |

### 크로스 네임스페이스 수집의 두 가지 권한

#### 1) RBAC — Prometheus SA가 클러스터 전역 read 가능해야 함

Operator는 설치 시 자동으로 ClusterRole 부여:

```
ClusterRole 'prom-prometheus' 권한:
  ""               services, endpoints, pods, nodes  ← 모든 NS에서 read
  discovery.k8s.io endpointslices
  networking.k8s.io ingresses
```

#### 2) Selector — 어느 ServiceMonitor를 발견할지

| Selector | 의미 |
|----------|------|
| `serviceMonitorSelector: {}` | 모든 라벨 매칭 |
| `serviceMonitorNamespaceSelector: {}` | 모든 네임스페이스 매칭 |
| `serviceMonitorNamespaceSelector: nil` | 같은 NS만 (가장 제한적) |

우리 구성: 두 selector 모두 `{}` → 전 클러스터 ServiceMonitor 자동 발견.

### ServiceMonitor를 어디에 둘지

| 패턴 | 장점 | 단점 |
|------|------|------|
| **앱 옆에 (앱 namespace)** — 추천 | 앱 매니페스트와 함께 버전 관리, GitOps 친화 | namespaceSelector 설정 필요 |
| **monitoring 네임스페이스에 모아두기** | 중앙 통제, 보안 정책 일원화 | 앱 변경 시 두 곳 수정 |

---

## 3. 모든 트래픽이 Service를 거치는데 어떻게 Pod별로 메트릭을 수집하나

**핵심: Prometheus는 Service ClusterIP로 트래픽을 보내지 않는다.**

### Service의 두 가지 경로

```
앱 트래픽:         curl http://actuator:8081
                     ↓
                  Service ClusterIP (가상 IP)
                     ↓ kube-proxy/iptables가 LB
                     ↓
              ┌──────┴──────┐
           Pod-A IP      Pod-B IP   (랜덤 1대만 선택)


Prometheus:    Service 객체를 보고 "어떤 Pod들이 이 서비스 뒤에 있나?"
                     ↓ Endpoints/EndpointSlice 조회
                     ↓
              ┌──────┴──────┐
           Pod-A IP      Pod-B IP   ← 각각 직접 HTTP 호출
            scrape          scrape
```

### 실제 객체 매핑

| 객체 | 주소 | 역할 |
|------|------|------|
| Service `actuator` | ClusterIP 10.96.197.20 | 앱들이 호출하는 가상 IP (LB) |
| EndpointSlice `actuator-v42kv` | 10.244.0.21 | 실제 Pod IP 목록 (selector로 자동 생성) |
| Pod `actuator-...cl8fc` | 10.244.0.21 | 진짜 컨테이너가 떠있는 IP |

Prometheus는 10.96.x.x가 아니라 **10.244.0.21에 직접** HTTP 요청.

### ServiceMonitor → scrape targets 변환

```
[ServiceMonitor]                                  [Prometheus]
  selector: app=actuator             Operator       scrape_configs:
  endpoints:                       ─ 가/번역 ─→       kubernetes_sd_configs:
    - port: http                                       role: endpoints
      path: /actuator/prometheus                       (or endpointslice)
                                                     relabel: app=actuator
                                                              port name=http
                                          ↓ K8s API watch
                                   EndpointSlice에서 Pod IP 목록을 받아옴
                                          ↓
                            매 scrape마다 모든 IP에 직접 GET
```

### Pod별 구분 — `instance` 라벨

```
http_server_requests_seconds_count{
  application="actuator",
  instance="10.244.0.21:8081",   ← Pod별로 다름
  pod="actuator-5656984bcb-cl8fc",
  namespace="monitoring-apps",
  uri="/api/users",
  ...
}
```

replicas=3이면 동일 메트릭이 3개의 다른 `instance` 라벨로 들어온다.

### 왜 굳이 Service가 필요한가

1. **셀렉터 추상화** — Pod 라벨이 바뀌어도 EndpointSlice가 자동 갱신
2. **포트 이름 매핑** — ServiceMonitor가 `port: http`로 지정하면 Service의 포트 이름으로 결정

### PodMonitor — Service 없이도 가능

Service를 만들기 싫으면 PodMonitor CR로 `kubernetes_sd_configs(role: pod)` 변환 가능. 하지만 대부분 기존 Service 재활용으로 ServiceMonitor를 더 많이 쓴다.

---

## 4. Prometheus가 클러스터 밖에 있으면 위 구조가 불가능한가

**직접적으로는 불가능.** Pod IP, ClusterIP는 CNI 오버레이 네트워크의 내부 주소로 외부에서 라우트 불가.

### 4가지 우회 패턴

#### 1) NodePort / LoadBalancer로 노출 — 권장 안 함

```
External Prometheus → NodePort 31234
                        ↓ kube-proxy LB
                ┌──────┴──────┐
              Pod-A         Pod-B   ← 매 scrape마다 랜덤
```

매 scrape가 랜덤 Pod에 가서 `instance` 라벨이 일정하지 않음. **per-pod 관측이 의미를 잃는다.**

#### 2) In-cluster Prometheus + remote_write — 업계 표준

```
┌─ 클러스터 내부 ─────────────────┐
│ Prometheus (scrape는 내부)        │
│         ↓ remote_write (HTTP push)│
└─────────┼────────────────────────┘
          ↓
┌─ 외부 (장기 저장소) ────────────┐
│ Thanos / Cortex / Mimir /        │
│ VictoriaMetrics / Grafana Cloud  │
└──────────────────────────────────┘
```

```yaml
prometheus:
  prometheusSpec:
    remoteWrite:
      - url: https://central-prometheus.example.com/api/v1/write
```

#### 3) Federation

```
External Prometheus
       ↓ GET /federate?match[]={...} (15s마다)
In-cluster Prometheus  ← scrape는 내부에서
```

외부 Prometheus가 내부 Prometheus의 `/federate`를 scrape. 해상도 낮음, 카디널리티 큰 시계열엔 부적합.

#### 4) Pod IP를 외부에서 라우터블하게 — 드뭄

Calico BGP, Cilium BGP, GKE VPC-native, AKS Azure CNI 등. 보안/운영 부담 큼.

### 의사결정

| 상황 | 추천 |
|------|------|
| 단일 클러스터 학습/개발 | In-cluster Prometheus (지금 우리 구성) |
| 멀티 클러스터, 장기 보관 | In-cluster scrape + remote_write to Thanos/Mimir |
| 외부에 이미 Prometheus + 일부 통합 | Federation |
| SaaS | SaaS 측 agent를 클러스터에 설치 (결국 in-cluster scrape) |

### 핵심 원칙

> **scrape는 항상 Pod와 같은 네트워크에서 일어나야 한다.** 메트릭을 클러스터 밖으로 옮기려면 pull 대신 push(remote_write) 또는 메타 pull(federation)로 한 단계 거친다.

---

## 5. ServiceMonitor 자세히

### 정의

Prometheus Operator가 제공하는 CRD (`monitoring.coreos.com/v1`). "Prometheus가 어떤 Service의 뒤에 있는 Pod들에서 어떤 경로로 메트릭을 긁어가야 하는지"를 선언적으로 명시.

기존 Prometheus의 `scrape_configs:` 한 블록을 떼어다 Kubernetes 리소스로 만든 것.

### reconcile 흐름

```
[당신]              [Operator]                    [Prometheus]
ServiceMonitor 작성
kubectl apply
       │
       ▼
   API server에 저장
       │
       │ (watch event)
       ▼
   Operator가 모든 ServiceMonitor 모음
       │
       ▼
   Prometheus 설정(YAML) 재생성
       │
       ▼
   Secret(prometheus-config)에 저장
                                                    │
                              ◄─ config-reloader가 ─┤
                              감지 후 SIGHUP        │
                                                    ▼
                                              새 scrape config 반영
                                                    │
                                                    ▼
                                      EndpointSlice 보고 Pod IP 발견
                                                    │
                                                    ▼
                                       각 Pod IP에 직접 HTTP scrape
```

### 전체 구조

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: actuator
  namespace: monitoring-apps
  labels:
    release: prom                   # Operator의 selector와 매칭되는 라벨

spec:
  jobLabel: app                     # Prometheus의 'job' 라벨로 쓸 Service label key
  selector:
    matchLabels:                    # Service의 metadata.labels 와 매칭
      app: actuator
  namespaceSelector:
    matchNames: [monitoring-apps]   # 또는 any: true

  endpoints:
    - port: http                    # Service.spec.ports[].name (숫자 X)
      path: /actuator/prometheus
      scheme: http
      interval: 15s
      scrapeTimeout: 10s
      honorLabels: false
      honorTimestamps: true

      basicAuth: { ... }            # 또는 bearerTokenSecret, oauth2
      tlsConfig: { ... }
      params: { format: ['prometheus'] }

      relabelings: [...]            # scrape 전, 타겟 단위
      metricRelabelings: [...]      # scrape 후, 메트릭 단위
```

### selector는 Service를 매칭한다 — Pod가 아니다

매칭이 두 단계:
1. ServiceMonitor의 selector → Service 발견
2. 발견된 Service의 spec.selector → EndpointSlice를 통해 Pod 발견

### port는 숫자가 아니라 이름

```yaml
# Service
spec:
  ports:
    - name: http
      port: 8081
      targetPort: http

# ServiceMonitor
spec:
  endpoints:
    - port: http        # ← 이름으로 참조
```

### namespaceSelector

| 설정 | 의미 |
|------|------|
| 생략 / `{}` | ServiceMonitor와 같은 네임스페이스에서만 |
| `any: true` | 모든 네임스페이스 |
| `matchNames: [ns1, ns2]` | 명시한 NS들만 |

### 자동으로 붙는 라벨

| 라벨 | 출처 |
|------|------|
| `job` | ServiceMonitor의 `<namespace>/<name>/<endpoint-index>` 또는 `jobLabel` |
| `instance` | Pod IP:port |
| `namespace` | Pod의 네임스페이스 |
| `pod` | Pod 이름 |
| `service` | Service 이름 |
| `container` | 컨테이너 이름 |
| `endpoint` | endpoint의 port 이름 |

### relabelings vs metricRelabelings

```
[K8s API에서 발견된 타겟 목록]
        ↓
[relabelings] ← 타겟을 scrape할지, 라벨 조작
        ↓
[scrape HTTP 호출]
        ↓
[들어온 메트릭에 자동 라벨 부착]
        ↓
[metricRelabelings] ← 메트릭별 라벨 조작, drop, rename
        ↓
[Prometheus에 저장]
```

**metricRelabelings 예시 — 카디널리티 폭발 방지**:
```yaml
metricRelabelings:
  - sourceLabels: [uri]
    regex: '/api/users/[0-9]+'
    targetLabel: uri
    replacement: '/api/users/{id}'
```

### 동작 확인 방법

```bash
# 1) Prometheus의 scrape config
kubectl -n monitoring exec prometheus-prom-prometheus-0 -c prometheus -- \
  wget -qO- http://localhost:9090/api/v1/status/config

# 2) Prometheus UI Targets
kubectl -n monitoring port-forward svc/prom-prometheus 9090:9090
# → http://localhost:9090/targets

# 3) Operator 로그
kubectl -n monitoring logs deployment/prom-operator | grep -i actuator
```

### 흔한 함정

| 증상 | 원인 |
|------|------|
| ServiceMonitor 만들었는데 Targets에 안 나타남 | `serviceMonitorSelector` release 라벨 매칭 필요 |
| 타겟은 나타나는데 DOWN | port 이름 불일치, path 잘못됨, 인증 누락 |
| 타겟이 너무 많이 잡힘 | namespaceSelector가 `any: true`인데 selector가 너무 느슨함 |
| 메트릭 카디널리티 폭발 | path/userId 같은 변동 라벨, `metricRelabelings`로 정규화 또는 drop |

---

## 6. ServiceMonitor는 능동적으로 뭔가 하는 객체인가

**아니다. ServiceMonitor는 능동적으로 아무것도 하지 않는다. 그냥 etcd에 적힌 YAML 문서일 뿐이다.**

### 4명의 등장인물

| 주체 | 하는 일 | 안 하는 일 |
|------|---------|-----------|
| **ServiceMonitor** (CRD/YAML) | 명세서 — "이 라벨의 Service를, 이 포트로, 이 경로로, 이 주기로 긁어라" | 자기 자신은 절대 scrape도 발견도 안 함. 그냥 종이 한 장. |
| **Prometheus Operator** | ServiceMonitor를 watch → 번역 → Prometheus 설정 파일(Secret) 생성. 설치/갱신 시에만 일함. | 런타임 scrape에는 끼지 않음. Pod 발견에도 끼지 않음. |
| **Prometheus 서버** (`kubernetes_sd_configs`) | 자기가 직접 K8s API server를 watch해서 Endpoints/EndpointSlice 모니터링 → Pod IP 목록 유지 → 직접 HTTP scrape | — |
| **K8s API server** | Pod, Service, Endpoints, EndpointSlice 객체 보관/제공 | — |

### 비유

```
ServiceMonitor      = 식당 메뉴판 (정적 명세)
Prometheus Operator = 식당 매니저 (메뉴판 보고 주방에 주문 전달)
Prometheus          = 요리사 + 배달원 (실제 가서 Pod에서 메트릭 가져옴)
K8s API server      = 주소록 (Pod IP 어디 있는지 알려줌)
```

### Operator는 런타임에 끼지 않는다

Operator는 **설정을 만들 때만** 일한다. Pod이 새로 떠도 / 죽어도 / 스케일아웃이 일어나도:

- Operator는 아무것도 안 함
- Prometheus가 자기 SD 캐시로 Pod IP 목록을 갱신함 (K8s API watch 덕분에 거의 실시간)

새 ServiceMonitor가 생기거나 기존 게 수정될 때만 Operator가 다시 일어나서 config Secret을 갱신.

---

## 7. Prometheus Operator는 뭐고, 필수인가

### 정체

Kubernetes Operator 패턴을 Prometheus에 적용한 컨트롤러. "Prometheus를 Kubernetes답게 운영"하는 게 목표.

```
일반 컨트롤러                        Prometheus Operator
─────────                          ───────────────────
Deployment ─→ ReplicaSet ─→ Pod    Prometheus(CR) ─→ StatefulSet ─→ Pod
                                   ServiceMonitor(CR) ─→ scrape config
                                   AlertmanagerConfig(CR) ─→ alertmanager.yml
                                   PrometheusRule(CR) ─→ rule files
```

### 책임

| 책임 | 설명 |
|------|------|
| **Prometheus CR → StatefulSet** | Prometheus, Alertmanager, ThanosRuler를 CR로 받아 실제 워크로드 생성 |
| **scrape config 자동 합성** | ServiceMonitor / PodMonitor / Probe CR을 모아 `prometheus.yml`로 번역, Secret에 저장 |
| **alerting rule 자동 합성** | PrometheusRule CR을 모아 rule 파일 생성 |
| **Alertmanager 설정** | AlertmanagerConfig CR을 모아 라우팅 트리/리시버 합성 |
| **TLS/Secret 관리** | 인증서, basic auth secret 자동 마운트 |
| **업그레이드 처리** | CR `spec.version` 바꾸면 StatefulSet 이미지/RBAC을 차례로 업데이트 |
| **샤딩/HA** | `replicas`, `shards` 필드로 분산 운영 자동화 |

### 필수가 아니다 — 대안

#### A) Operator 없이 plain Prometheus
- `prometheus-community/prometheus` chart (kube-prometheus-stack과 다름)
- annotation 기반 (`prometheus.io/scrape: "true"`) 또는 `kubernetes_sd_configs`로 발견
- ServiceMonitor 같은 CRD 없음. Pod에 annotation만 붙이면 끝

#### B) kube-prometheus-stack (현재 우리 구성)
- Operator + Prometheus + Grafana + Alertmanager + exporters 한 번에
- 사실상 업계 표준

#### C) Prometheus 아예 안 쓰기
- Grafana Agent / Alloy, OpenTelemetry Collector, vmagent, SaaS agent

### 의사결정

| 상황 | 추천 |
|------|------|
| 1-2명 개발팀, 서비스 5개 이하 | plain Prometheus + annotation |
| 회사 전체, 여러 팀이 모니터링 대상 추가 | kube-prometheus-stack (Operator) |
| HA, 샤딩, 멀티테넌시 | Operator 필수 |
| 멀티 클러스터, 중앙 집중 | vmagent / Grafana Agent + 중앙 저장소 |

### Operator의 단점

| 단점 | 영향 |
|------|------|
| **CRD 7-10개 설치** | API server 부담, 보안 검토 대상 증가 |
| **추가 Pod (operator)** | 메모리 100-200Mi 더 |
| **개념 학습곡선** | Prometheus + CRD + Helm values 3중 추상화 |
| **디버깅 어려움** | "왜 scrape 안 되지?"가 → ServiceMonitor 선택자? Operator selector? Prometheus CR selector? |
| **CRD 업그레이드 위험** | major 업그레이드 시 CRD 깨지면 전부 마비 |

---

## 8. CR, CRD는 무엇인가

### 기본: 쿠버네티스의 "리소스"

쿠버네티스는 모든 것을 **리소스(=객체)**로 다룬다. Pod, Service, Deployment, ConfigMap... 이런 built-in 리소스는 API server에 정의되어 있고 etcd에 저장됨.

### 한계 — "Prometheus를 표현할 리소스가 없다"

쿠버네티스는 Pod/Service만 알지, "Prometheus 서버"라는 개념은 모름. Prometheus를 운영하려면 StatefulSet + ConfigMap + Service + Secret + ServiceAccount 등을 직접 조합해야 함. 어렵고 실수 잦음.

해결책: **"Prometheus라는 새로운 리소스 타입을 쿠버네티스 API에 등록해버리자."** 그래서 CRD가 등장.

### 정의

| 약자 | 풀이 | 의미 | 비유 |
|------|------|------|------|
| **CRD** | Custom Resource Definition | 새 리소스 타입을 추가하는 **스키마 등록** | Java의 `class` 정의 / DB 테이블 schema |
| **CR** | Custom Resource | 그 CRD를 따르는 실제 객체 (인스턴스) | `new MyClass()` / DB row |

### CRD 등록 시 일어나는 일

```bash
kubectl apply -f prometheus-crd.yaml
```

이 한 줄로:
1. K8s API server에 새 엔드포인트 생성 (예: `/apis/monitoring.coreos.com/v1/prometheuses`)
2. `kubectl get prometheus`, `kubectl get servicemonitor` 즉시 동작
3. 새 리소스를 etcd에 저장 가능

→ API server를 재컴파일하지 않고 새 리소스를 추가할 수 있게 됨. 쿠버네티스 확장성의 핵심.

### Controller가 짝꿍

CRD만 등록하면 etcd에는 저장되지만 아무 일도 안 일어난다. **Controller가 watch해서 처리**해야 의미가 생긴다.

```
당신 ──→ Prometheus CR을 apply
                │
                ▼
        etcd에 저장됨
                │ (watch event)
                ▼
    Prometheus Operator가 감지
                │
                ▼
   "이 명세대로 StatefulSet/Service/Secret을 만들자"
                │
                ▼
  built-in 리소스들로 실제 워크로드 생성
```

**CRD + CR + Controller** 3개가 모여야 실제 작동 — 이 패턴이 **Operator Pattern**.

### CRD 명명 규칙

```
<plural>.<group>
```

예: `servicemonitors.monitoring.coreos.com` → 그룹 `monitoring.coreos.com`, 복수형 `servicemonitors`
사용 시: `kind: ServiceMonitor`, `apiVersion: monitoring.coreos.com/v1`

### 다른 도구의 CRD 예시

| 도구 | CRD |
|------|-----|
| Argo CD | `Application`, `AppProject` |
| Istio | `VirtualService`, `Gateway`, `DestinationRule` |
| cert-manager | `Certificate`, `Issuer`, `ClusterIssuer` |
| Knative | `Service`, `Revision`, `Route` |
| Strimzi (Kafka) | `Kafka`, `KafkaTopic`, `KafkaUser` |

### 장단점

**장점**:
- 새 도메인 객체를 K8s API로 통합 → `kubectl get`, RBAC, audit log 그대로 작동
- 선언적 GitOps와 자연스럽게 어울림
- OpenAPI schema 기반 validation

**단점**:
- 무분별 설치 시 클러스터가 무거워짐 (API server watch 부담)
- 도구마다 다른 CRD라 학습 부담
- 도구 제거 시 CRD 청소 깔끔하지 않음

---

## 9. Operator와 Prometheus는 어떻게 통신하나

**핵심: 둘 다 서로를 직접 호출하지 않는다. Secret을 가운데 두고 간접 통신.**

### 전체 흐름

```
[Operator]                                    [Prometheus Pod]
   │                                                │
   │ 1. K8s API watch                               │
   ▼                                                │
ServiceMonitor / Prometheus CR 변화 감지            │
   │                                                │
   │ 2. prometheus.yml 생성                         │
   ▼                                                │
   │ 3. Secret(prometheus-prom-prometheus)          │
   │    에 config 업로드 ──────────────────────────▶│ Secret이 Pod에 파일로 mount됨
   │                                                │   /etc/prometheus/config/prometheus.yaml.gz
   │                                                │
   │                                                │ 4. kubelet이 mount된 파일을 갱신
   │                                                │
   │                                                │ 5. config-reloader 사이드카가
   │                                                │    파일 변경 감지 (inotify)
   │                                                │
   │                                                │ 6. localhost:9090/-/reload 호출
   │                                                │       ▼
   │                                                │    Prometheus 서버가 새 config 읽음
```

### 확인 — Prometheus Pod의 컨테이너 구성

```
prometheus       : quay.io/prometheus/prometheus
config-reloader  : quay.io/prometheus-operator/prometheus-config-reloader
```

config-reloader의 핵심 인자:
```
--reload-url=http://127.0.0.1:9090/-/reload   ← 같은 Pod 안의 Prometheus 호출
--config-file=/etc/prometheus/config/prometheus.yaml.gz  ← 감시 대상 파일
```

Secret 라벨: `managed-by: prometheus-operator`

### 왜 이렇게 설계했나

| 직접 호출 안 하는 이유 | 설명 |
|--------------------------|------|
| **결합도 분리** | Operator가 죽어도 Prometheus 계속 동작 (마지막 config로). 반대도 마찬가지 |
| **재시작/스케일 강함** | Prometheus Pod 재시작해도 Secret에서 config 재로드 |
| **K8s 네이티브 패턴** | "선언적 desired state → controller reconcile → 공유 저장소" 표준 |
| **권한 단순화** | Operator는 Secret만 쓰면 됨. Prometheus를 직접 호출 X |
| **재시도/순서 무관** | 동시에 여러 ServiceMonitor 변경되도 한 번에 통합 Secret을 만들면 됨 |

### 각 단계 상세

#### 1) Operator의 watch
```
GET /apis/monitoring.coreos.com/v1/servicemonitors?watch=true
GET /apis/monitoring.coreos.com/v1/prometheuses?watch=true
```
long-polling으로 ADD/UPDATE/DELETE 이벤트 수신.

#### 2) Operator의 config 생성
1. 관련 ServiceMonitor 전부 모음
2. Prometheus CR의 selector로 필터
3. 각각을 `scrape_configs:` 항목으로 변환
4. 전체를 `prometheus.yaml`로 합침
5. gzip 압축 (Secret 1MB 제한 회피)

#### 3) Secret 업데이트
```
PATCH /api/v1/namespaces/monitoring/secrets/prometheus-prom-prometheus
body: { data: { "prometheus.yaml.gz": "<gzip된 config>" } }
```

#### 4) kubelet의 mount 갱신
Pod이 Secret을 volume으로 마운트하고 있을 때, kubelet이 주기적으로(~60초 또는 더 짧게) Secret 변경 감지 후 Pod 내부 파일 업데이트. **컨테이너 재시작 없이.**

#### 5) config-reloader 감지
사이드카가 마운트 파일을 inotify로 감시.

#### 6) Prometheus에 HTTP 호출
```
POST http://127.0.0.1:9090/-/reload
```
같은 Pod 내 localhost. Prometheus가 새 config 다시 읽고 SD/scrape 재설정.

### 전형적 지연

```
ServiceMonitor 수정
     ↓ ~1s (Operator watch 이벤트)
Operator가 config 재생성
     ↓ ~1s (Secret PATCH)
etcd 저장
     ↓ kubelet 폴링 주기 (수십 초)
Pod 파일시스템 갱신
     ↓ ~1s (config-reloader 감지)
Prometheus reload
     ↓ ~1s (HTTP /-/reload)
새 scrape config 활성
```

→ 보통 **30초 이내**에 반영. 즉시 안 보여도 당황 X.

### 비유

```
당신이 식당에 새 메뉴 종이(ServiceMonitor)를 추가
     ↓
매니저(Operator)가 메뉴판 통합본(Secret) 다시 인쇄해서
     ↓ 주방 게시판에 붙임
배달원(kubelet)이 게시판 사진을 주기적으로 찍어다가
     ↓ 요리사 옆 책상(Pod 내 파일)에 새 사진 놓음
보조(config-reloader)가 책상 위 사진 바뀐 걸 보고
     ↓ "헤이 요리사! 새 주문서 보세요" (HTTP /-/reload)
요리사(Prometheus)가 새 주문서대로 요리(scrape) 시작
```

매니저와 요리사는 **단 한 번도 직접 대화하지 않는다**. 모든 게 게시판/책상 위 종이를 통해 비동기로 흘러간다.

이게 쿠버네티스 컨트롤러 패턴의 표준이고, cert-manager, ArgoCD, Istio 등 모든 Operator가 비슷한 방식으로 동작한다.
