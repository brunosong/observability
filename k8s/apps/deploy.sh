#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-db-migration}"
TAG="${TAG:-dev}"
REGISTRY="${REGISTRY:-localhost:5000}"
MODULES=(otel actuator loadgen)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${ROOT_DIR}"

echo ">> Building boot jars (otel, loadgen)"
./gradlew :otel:bootJar :loadgen:bootJar --console=plain -q

echo
echo ">> Building & pushing actuator image via Jib -> ${REGISTRY}/monitoring/actuator:${TAG}"
./gradlew :actuator:demo:jib \
    -PimageRegistry="${REGISTRY}" \
    -PimageRepo=monitoring/actuator \
    -PimageTag="${TAG}" \
    --console=plain

for mod in otel loadgen; do
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
echo ">> Restarting actuator to pull fresh image from registry"
kubectl -n monitoring-apps rollout restart deployment/actuator

echo
echo ">> Waiting for rollouts"
for mod in "${MODULES[@]}"; do
    kubectl -n monitoring-apps rollout status deployment/"${mod}" --timeout=180s
done

echo
echo ">> Done."
kubectl -n monitoring-apps get pods,svc
