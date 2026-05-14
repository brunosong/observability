# k8s — Monitoring Stack

kind 클러스터에 `kube-prometheus-stack` (Prometheus + Grafana + Alertmanager + node-exporter + kube-state-metrics)을 설치하는 스크립트.

## Prerequisites

- 실행 중인 kind 클러스터 (`kind get clusters`로 확인)
- `kubectl` (현재 context가 대상 클러스터인지 확인)
- `helm` v3 이상

## Install

```bash
./k8s/install.sh
```

- 기본 네임스페이스: `monitoring`
- 기본 릴리즈명: `prom`
- 다른 이름을 원하면 `NAMESPACE=... RELEASE=... ./k8s/install.sh`

## Access

설치 후 별도 터미널에서:

```bash
# Grafana — http://localhost:3000 (admin / admin)
kubectl -n monitoring port-forward svc/prom-grafana 3000:80

# Prometheus — http://localhost:9090
kubectl -n monitoring port-forward svc/prom-prometheus 9090:9090

# Alertmanager — http://localhost:9093
kubectl -n monitoring port-forward svc/prom-alertmanager 9093:9093
```

## Uninstall

```bash
./k8s/uninstall.sh
```

CRD까지 함께 제거합니다.

## Notes

- kind 클러스터 한정: control-plane 컴포넌트 (etcd/scheduler/controller-manager/kube-proxy) 메트릭은 노출 어려워 비활성화.
- 영구 볼륨 미사용 — Pod 재시작 시 메트릭/대시보드 데이터는 초기화됩니다. kind 환경 가정.
- `ServiceMonitor`/`PodMonitor`는 모든 네임스페이스에서 자동 발견되도록 설정되어 있어, 추후 Spring Boot 앱에 메트릭 노출 후 ServiceMonitor만 추가하면 Prometheus가 자동으로 수집합니다.
