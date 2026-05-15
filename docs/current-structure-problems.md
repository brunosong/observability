# 현 구조의 문제점: 관측 부재 운영 리스크

현 구조에서 관측·모니터링은 선택적 부가 기능으로 인식되는 경향이 있다. 그 근저에는 "장애가 발생해도 결국 알아서 풀린다"는 관점이 있다. 본 문서는 이 관점이 **시스템의 속성이 아니라 관측 부재가 만들어내는 인지적 착각**임을 네 영역(Pod, Node, ArgoCD, DB)에 걸쳐 분석한다.

## 목차

1. [핵심 명제](#1-핵심-명제)
2. [Pod — 가장 자주, 가장 가려짐](#2-pod--가장-자주-가장-가려짐)
3. [Node — Cascade의 부모](#3-node--cascade의-부모)
4. [ArgoCD — 의존성 다발](#4-argocd--의존성-다발)
5. [DB — Stateful, 거짓 회복](#5-db--stateful-거짓-회복)
6. [통합 비교](#6-통합-비교)
7. [결론](#7-결론)

---

## 1. 핵심 명제

### 1.1 "풀렸다"는 진술은 검증 불가능하다

측정이 없으면 다음 셋을 구분할 수 없다.

| 보이는 것 | 실제 |
|---|---|
| A. 풀렸다 | 원인이 해소되어 정상 복귀 |
| B. 풀린 것처럼 보인다 | 사용자가 이탈했거나 앞단이 먼저 죽어 신호가 끊김 |
| C. 풀린 게 아니라 옮겨갔다 | 큐 적체, 타임아웃 누적, 다른 컴포넌트로 전파 |

### 1.2 자동 복구는 장애를 없애지 않는다 — 가린다

Kubernetes의 self-healing은 강력하지만, 본질적으로 **장애의 사실 자체를 사람의 눈에서 가리는 메커니즘**이다. 자동 복구가 작동했다는 것과 장애가 없었다는 것은 다른 명제이며, 관측이 없으면 이 둘이 같은 것처럼 보인다.

### 1.3 리스크는 빈도 × 영향도 × 인지 가능성으로 평가한다

장애 빈도만으로는 우선순위를 결정할 수 없다. 영향 범위와 인지 가능성을 함께 보면 "자주 죽는 Pod가 가장 안전하다"는 직관은 정반대로 뒤집힌다.

---

## 2. Pod — 가장 자주, 가장 가려짐

**빈도:** 하루 수십 번. 자동 재시작이 가려서 보이지 않을 뿐.

### 2.1 주요 장애 원인

| 분류 | 대표 사례 |
|---|---|
| 시작 실패 | ImagePullBackOff, CreateContainerConfigError, Init 무한 실패, CrashLoopBackOff |
| 스케줄링 실패 | 리소스 부족, NodeSelector/Affinity, Taint/Toleration, PVC bind, PDB |
| 런타임 사망 | OOMKilled, Evicted, Liveness 실패, 앱 panic |
| Probe 문제 | Readiness silent fail, 공격적 liveness, startup probe 미설정 |
| 리소스/QoS | CPU throttling, 메모리 limit 부족, ephemeral-storage 초과 |
| 네트워크 | CoreDNS, sidecar 미start, NetworkPolicy 차단 |
| 볼륨 | PV mount 실패, RWO 충돌, CSI 문제 |
| Rollout | 잘못된 이미지 태그, maxUnavailable 과다, HPA 오동작 |
| 사람 | `kubectl delete` 실수, namespace 삭제, manifest 사고 |

### 2.2 "알아서 풀린다"가 환상인 이유

1. **`Running 1/1` ≠ 정상.** RESTARTS가 누적되어도 status는 `Running`. 앱이 hang인 컨테이너도 동일.
2. **CrashLoopBackOff의 거짓 회복.** exponential backoff로 재시도 간격이 최대 5분. "5분 뒤 정상"이 아니라 "5분에 한 번만 시도하는 멈춘 상태".
3. **Readiness silent failure.** Pod는 `Running`이지만 Service에서 빠져 트래픽을 못 받음. `kubectl get pods`로는 정상으로 보임.
4. **부분 cascade.** replica 1개 사망 → 나머지 부하 증가 → 또 사망 → 갑작스러운 전체 다운.
5. **메모리 누수의 점진적 가속.** 5시간마다 OOMKill되던 것이 트래픽과 함께 30초마다로 악화. 사람이 본 신호는 "좀 느린데?" 뿐.

> Pod는 자동 복구가 가장 잘 작동하는 영역이라서 가장 위험하다. 장애가 나지 않은 것이 아니라, 장애의 사실이 시스템 내부에서만 순환하고 사람의 인지 영역에 닿지 않는 것.

---

## 3. Node — Cascade의 부모

**빈도:** 디스크 압박 주 단위, 노드 사망 월 단위, kernel panic 분기 단위, AZ outage 연 단위(치명적).

### 3.1 주요 장애 원인

| 분류 | 대표 사례 |
|---|---|
| 하드웨어/OS | Kernel panic, 베어메탈 장애, hypervisor 문제, FS 손상 |
| 리소스 압박 | MemoryPressure, DiskPressure, PIDPressure, IOPS 한계 |
| kubelet/런타임 | kubelet crash, containerd 죽음, API server 통신 실패, 인증서 rotation 실패 |
| 네트워크 | 노드 단절, CNI 장애, iptables 손상, kube-proxy 죽음, MTU 불일치 |
| 클라우드 | Spot 회수, scheduled maintenance, AZ outage, autoscaler 오결정 |
| 운영 변경 | OS 패치 부팅 실패, kubelet 업그레이드 호환성, sysctl 오설정, drain 실패 |
| 부하/스케일 | max-pods 한계, anti-affinity 미설정으로 무거운 Pod 몰림, noisy neighbor |

### 3.2 "알아서 풀린다"가 환상인 이유

1. **Reschedule 비용.** 새 노드 provisioning, 이미지 pull, 캐시 cold start, JVM warmup — 그 사이 SLA 위반.
2. **다른 노드도 capacity 없음.** 빠듯하게 운영되는 클러스터는 Pod 옮길 곳이 없어 Pending 누적.
3. **PV가 특정 AZ에만 있음.** 다른 AZ 노드로 옮긴 Pod이 mount 실패. RWO 볼륨은 특히.
4. **동시 다발 사망.** AZ outage, autoscaler 오작동, daemonset 잘못된 update로 여러 노드 동시 영향 → cluster capacity 한계 초과.
5. **조용한 노드 이상.** 디스크 80% 차있는 노드, kubelet 메모리 누수, PIDPressure 임계 직전 — 관측 없이는 100% 놓침. 어느 날 cascade로 폭발.

### 3.3 Cascade 특성

> 노드 위에 Pod 100개가 있다면 노드 1개 사망 = 100개 Pod 사고. 그 100개가 서로 다른 서비스라면 사실상 소규모 클러스터 장애.

---

## 4. ArgoCD — 의존성 다발

**빈도:** Sync 실패 주 단위, drift 주 단위, 토큰 만료 분기 단위, Git outage 분기 단위.

### 4.1 의존성 모델

```
Git 서버 ─┐
인증 토큰 ─┤
DNS ──────┤
대상 클러스터 API ─┤
Container Registry ─┼─► ArgoCD ─► 사용자
RBAC 정책 ──────────┤
Manifest 정합성 ────┤
사람의 수동 변경 ────┘
```

각 의존성 가용성이 99.9%라도 8개를 곱하면 약 99.2% = 월 평균 5.7시간 장애. 현실은 더 낮다.

### 4.2 주요 장애 원인

| 분류 | 대표 사례 |
|---|---|
| 내부 컴포넌트 | application-controller OOM, repo-server OOM, Redis 캐시 손상, controller sharding 한계 |
| Git | Git outage, 토큰/SSH key 만료, 권한 변경, webhook 미수신, repo URL 변경 |
| 대상 클러스터 | API server 응답 지연, 인증 토큰 만료, RBAC 변경, CRD 미설치, admission webhook reject |
| 매니페스트 | Helm 버전 변경, Kustomize 빌드 실패, values 누락, sync wave 오류, hook 실패 |
| 운영/조직 | 수동 drift, Terraform과 충돌, self-managed ArgoCD가 자기를 brick |
| 외부 의존성 | Registry 장애로 sync 성공 + ImagePullBackOff, DNS, TLS 만료, 외부 secret 도구 장애 |

### 4.3 "알아서 풀린다"가 환상인 이유

- **Sync 성공 ≠ 배포 성공.** manifest 적용은 됐는데 Pod는 CrashLoop인 경우, ArgoCD는 정상으로 본다.
- **반복 실패가 안 보임.** exponential backoff로 재시도 간격이 시간 단위까지 늘어남.
- **Self-managed의 함정.** 자기 매니페스트를 잘못 sync하면 자기를 brick. 외부 개입 없이 복구 불가.
- **Drift 감지 윈도우.** 기본 3분 polling. 컨트롤러가 멈추면 영원히 안 잡힘.
- **Webhook 미수신을 인지 불가.** Git push가 전달됐는지 알 방법이 없음.

> ArgoCD는 단일 도구가 아니라 외부 의존성의 집합체다. "장애가 안 난다"는 명제는 모든 의존성이 영구히 정상이라는 비현실적 가정 위에서만 성립한다.

---

## 5. DB — Stateful, 거짓 회복

**빈도:** Slow query 일 단위, Deadlock 일~주 단위, Disk full 임박 분기 단위, Failover 실패는 드물지만 발생 시 데이터 손실.

### 5.1 주요 장애 원인

| 분류 | 대표 사례 |
|---|---|
| 인스턴스 | Disk full, OOM, IOPS 한계, CPU 100%, FS 손상, WAL/redo 폭증 |
| 쿼리 | Slow query, N+1, long transaction, deadlock, connection pool exhaustion |
| 스키마/데이터 | 인덱스 부재/과다, 통계 stale, sequence 한계, 데이터 폭증, partition 미적용 |
| 운영 변경 | Migration lock, 잘못된 백업/복구, prod 수동 DELETE, VACUUM 과부하, 인증 로테이션 실패 |
| 복제/HA | Replication lag, failover 실패, **silently 깨진 standby**, sync replication hang |
| 외부 의존성 | Storage 장애, network 분리, DNS, TLS 만료, KMS 회수, managed DB outage |
| 트래픽 | 트래픽 폭증, cold cache, cache 무효화 thundering herd, OLTP에서 reporting 쿼리 |

### 5.2 "풀린 것처럼 보이는" 메커니즘 — 거의 모두 거짓 회복

| 보이는 현상 | 실제로 일어난 일 |
|---|---|
| 부하가 사라졌다 | 사용자가 포기하고 떠남 (트래픽 = 이탈) |
| 응답 시간이 정상화됐다 | 앱이 먼저 타임아웃 / circuit breaker로 사망 → DB 큐가 빔 |
| Lock이 풀렸다 | Long transaction 잡고 있던 앱이 사망 |
| Connection이 정상이다 | 앱 재시작으로 pool reset |
| 디스크가 안 찬다 | 사실 차는 중인데 측정 안 함 |

### 5.3 DB의 특수성

- **Stateful.** 자가 복구 메커니즘이 거의 없음. 한번 깨진 상태는 외부 개입 없이 회복 불가.
- **Blast radius 최대.** DB가 죽으면 의존하는 모든 서비스가 즉시 사망.
- **자가 복구 불가능한 시나리오:** Connection pool exhaustion(영구 hang), Long transaction(점진 악화), Disk full(write 자체 중단), Replication lag(누적 시 회복 불가).

> DB는 "알아서 풀린다"가 가장 명백하게 거짓이 되는 영역이다. 풀린 것처럼 보이는 모든 경우는 측정 부재의 효과거나, DB가 회복된 게 아니라 앱이 항복한 결과다.

---

## 6. 통합 비교

| 항목 | Pod | Node | ArgoCD | DB |
|---|---|---|---|---|
| 장애 빈도 | 하루 수십 번 | 주~월 | 주~분기 | 매일 잔 신호 |
| 자가 복구 | 있음 (최대 함정) | 부분적 | 부분적 | 거의 없음 |
| Blast radius | replica → cascade | 노드 위 모든 Pod | 신규 배포 차단 → 점진 | 전 서비스 즉시 다운 |
| "알아서 풀린다" 환상 | 최강 | 강 | 중 | 거의 없음 (있다면 거짓) |
| 사람 눈으로 인지 | 거의 불가 | 어려움 | 어려움 | 즉시 (단, partial은 불가) |
| 측정 없이 위험도 | 최고 (정상으로 위장) | 고 (cascade 폭발) | 중-고 (의존성 다발) | 고 (비가역) |

---

## 7. 결론

### 7.1 본 분석의 결론 명제

1. "알아서 풀린다"는 시스템 속성이 아니라 측정 부재가 만드는 인지적 착각이다.
2. 자동 복구는 장애를 없애는 것이 아니라 사람의 눈에서 가린다. 가려진 장애는 누적되다가 임계점에서 폭발한다.
3. 각 영역의 장애 확률은 영역 자체의 안정성이 아니라 **의존성의 곱과 사람의 개입 빈도의 함수**다.
4. 자가 복구가 가장 잘 동작하는 영역(Pod)이 역설적으로 가장 위험하다. 복구되어 보이는 것과 정상인 것은 다르기 때문이다.
5. 자가 복구가 거의 안 되는 영역(DB)에서는 "알아서 풀린다"가 명백한 거짓이다.

### 7.2 모니터링의 정의 재정립

> 모니터링은 장애를 막기 위한 도구가 아니다.
> **모니터링은 장애가 일어났다는 사실 자체를 시스템이 사람에게 통보할 수 있게 하는 최소 요건이다.**

### 7.3 모니터링 부재가 만드는 현 운영 패턴

1. "매일 아침 누가 UI를 들여다보기"가 운영 절차가 되어 있다 — 절차의 신뢰성이 사람의 컨디션에 종속.
2. 장애 인지 경로가 "고객 문의 → CS → 개발팀"이다 — MTTD가 분~수십 분 단위.
3. 장애 후 사후 분석이 불가능하다 — RCA가 추측에 의존, 같은 장애 반복.
4. Silent degradation을 영원히 못 잡는다 — 95p / 99p 분리, 특정 테넌트 영향 등은 측정 없이 발견 불가.
5. Capacity planning이 사람의 감각에 의존한다 — 추세, 변경 영향, 회귀 감지 모두 불가.
6. 자동 복구의 누적이 폭발할 때까지 보이지 않는다 — Pod restart 누적, 노드 디스크 압박, DB connection leak.
7. 자동화의 입력이 없으므로 자동화 자체가 불가능하다 — Auto-scaling, circuit breaker, alerting-based remediation 모두 메트릭 기반.

### 7.4 결론 문장

> 측정하지 않는 것은 관리하지 않는 것이며, 관리하지 않는 것은 운(運)에 맡기는 것이다. 인프라를 운에 맡기겠다는 선언은 사업을 운에 맡기겠다는 선언과 같다.
>
> "알아서 풀린다"는 시스템의 속성이 아니라, **장애가 났는데 아무도 못 본 시간**의 다른 표현이다.
