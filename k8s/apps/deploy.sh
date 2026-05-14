#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-db-migration}"
TAG="${TAG:-dev}"
MODULES=(otel actuator loadgen)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${ROOT_DIR}"

echo ">> Building boot jars"
./gradlew $(printf ':%s:bootJar ' "${MODULES[@]}") --console=plain -q

for mod in "${MODULES[@]}"; do
    echo
    echo ">> Building image monitoring/${mod}:${TAG}"
    docker build -t "monitoring/${mod}:${TAG}" "${mod}/"

    echo ">> Loading into kind cluster '${CLUSTER_NAME}'"
    kind load docker-image "monitoring/${mod}:${TAG}" --name "${CLUSTER_NAME}"
done

echo
echo ">> Applying namespace first"
kubectl apply -f "${SCRIPT_DIR}/namespace.yaml"

echo ">> Applying workload manifests"
kubectl apply -f "${SCRIPT_DIR}/otel.yaml" \
              -f "${SCRIPT_DIR}/actuator.yaml" \
              -f "${SCRIPT_DIR}/loadgen.yaml"

echo
echo ">> Waiting for rollouts"
for mod in "${MODULES[@]}"; do
    kubectl -n monitoring-apps rollout status deployment/"${mod}" --timeout=180s
done

echo
echo ">> Done."
kubectl -n monitoring-apps get pods,svc
