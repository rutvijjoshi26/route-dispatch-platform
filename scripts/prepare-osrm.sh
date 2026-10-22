#!/usr/bin/env bash
# Downloads the Chicago OpenStreetMap extract and builds the OSRM car-routing
# dataset (MLD pipeline). Safe to re-run: each step is skipped when its output exists.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="${ROOT}/data/osm"
PBF_URL="${OSM_EXTRACT_URL:-https://download.bbbike.org/osm/bbbike/Chicago/Chicago.osm.pbf}"
OSRM_IMAGE="${OSRM_IMAGE:-ghcr.io/project-osrm/osrm-backend:v5.27.1}"

mkdir -p "${DATA_DIR}"

if [[ ! -f "${DATA_DIR}/chicago.osm.pbf" ]]; then
  echo "Downloading OSM extract from ${PBF_URL}"
  curl -fL --retry 3 -o "${DATA_DIR}/chicago.osm.pbf.part" "${PBF_URL}"
  mv "${DATA_DIR}/chicago.osm.pbf.part" "${DATA_DIR}/chicago.osm.pbf"
fi

run_osrm() {
  docker run --rm -v "${DATA_DIR}:/data" "${OSRM_IMAGE}" "$@"
}

if [[ ! -f "${DATA_DIR}/chicago.osrm.ebg" ]]; then
  echo "osrm-extract (car profile)"
  run_osrm osrm-extract -p /opt/car.lua /data/chicago.osm.pbf
fi
if [[ ! -f "${DATA_DIR}/chicago.osrm.partition" ]]; then
  echo "osrm-partition"
  run_osrm osrm-partition /data/chicago.osrm
fi
if [[ ! -f "${DATA_DIR}/chicago.osrm.cell_metrics" ]]; then
  echo "osrm-customize"
  run_osrm osrm-customize /data/chicago.osrm
fi

echo "OSRM dataset ready in ${DATA_DIR}"
