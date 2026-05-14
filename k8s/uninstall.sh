#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-monitoring}"
RELEASE="${RELEASE:-prom}"

echo ">> Uninstalling helm release '${RELEASE}' from namespace '${NAMESPACE}'"
helm uninstall "${RELEASE}" --namespace "${NAMESPACE}" || true

echo ">> Deleting leftover CRDs (kube-prometheus-stack does not remove these on uninstall)"
kubectl delete crd \
    alertmanagerconfigs.monitoring.coreos.com \
    alertmanagers.monitoring.coreos.com \
    podmonitors.monitoring.coreos.com \
    probes.monitoring.coreos.com \
    prometheusagents.monitoring.coreos.com \
    prometheuses.monitoring.coreos.com \
    prometheusrules.monitoring.coreos.com \
    scrapeconfigs.monitoring.coreos.com \
    servicemonitors.monitoring.coreos.com \
    thanosrulers.monitoring.coreos.com \
    --ignore-not-found

echo ">> Deleting namespace '${NAMESPACE}'"
kubectl delete namespace "${NAMESPACE}" --ignore-not-found
