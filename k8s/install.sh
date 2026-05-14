#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-monitoring}"
RELEASE="${RELEASE:-prom}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALUES_FILE="${SCRIPT_DIR}/kube-prometheus-stack-values.yaml"

echo ">> Adding prometheus-community helm repo"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update prometheus-community >/dev/null

echo ">> Ensuring namespace '${NAMESPACE}'"
kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${NAMESPACE}"

echo ">> Installing/upgrading release '${RELEASE}' (this may take a few minutes)"
helm upgrade --install "${RELEASE}" prometheus-community/kube-prometheus-stack \
    --namespace "${NAMESPACE}" \
    -f "${VALUES_FILE}" \
    --wait --timeout 10m

echo
echo ">> Done. Access UIs with port-forward:"
echo "   kubectl -n ${NAMESPACE} port-forward svc/prom-grafana 3000:80"
echo "   kubectl -n ${NAMESPACE} port-forward svc/prom-prometheus 9090:9090"
echo "   kubectl -n ${NAMESPACE} port-forward svc/prom-alertmanager 9093:9093"
echo
echo "   Grafana login: admin / admin"
