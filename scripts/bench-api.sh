#!/usr/bin/env bash
# API benchmarks against the running Docker stack:
#  1. GPS ingest load test with 50-400 simulated vans (each its own driver account),
#  2. re-plan latency for orders added to the demo day while 8 simulated vans are driving it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="${API:-http://localhost:8080}"
LEVELS="${LEVELS:-50,100,200,400}"
COMPOSE="${COMPOSE:-docker compose}"
cd "${ROOT}"

LOAD_TEST_DRIVERS=400 ${COMPOSE} up -d --build backend
./scripts/wait-for-health.sh "${API}"

./gradlew -q :simulator:installDist
SIM=simulator/build/install/driver-simulator/bin/driver-simulator

# One ping per van per second is a typical phone setting; the higher rates look for the ingest limit.
"${SIM}" load --api "${API}" --levels "${LEVELS}" --rate 1 --duration 60 --warmup 10
"${SIM}" load --api "${API}" --levels 400 --rate 5 --duration 60 --warmup 10
"${SIM}" load --api "${API}" --levels 400 --rate 10 --duration 60 --warmup 10

"${SIM}" drive --api "${API}" --prepare --time-scale 30 --late-van none > benchmarks/results/replan-drive.log 2>&1 &
DRIVERS=$!
trap 'kill ${DRIVERS} 2>/dev/null || true' EXIT
# Let the vans drive until mid-morning (plan time) so a realistic number of stops is pinned.
sleep 240
"${SIM}" replan --api "${API}" --runs 10
