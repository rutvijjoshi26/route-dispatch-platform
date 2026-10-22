# Route Dispatch Platform: common tasks. Requires Docker, JDK 21 and Node 22+.
COMPOSE := $(shell docker compose version >/dev/null 2>&1 && echo "docker compose" || echo "docker-compose")
GRADLE  := ./gradlew
API     ?= http://localhost:8080
SPEED   ?= 30
LEVELS  ?= 50,100,200,400

.PHONY: help setup run stop reset logs simulate test test-backend test-frontend lint benchmark bench-api sample-data

help:
	@echo "setup        download the Chicago map, build the OSRM dataset, install dependencies"
	@echo "run          start PostGIS, OSRM, backend and UI (http://localhost:3000)"
	@echo "simulate     optimize + dispatch the demo day and drive 8 simulated vans (SPEED=$(SPEED)x)"
	@echo "test         backend (JUnit, Testcontainers, JaCoCo) and frontend (Vitest) tests"
	@echo "lint         frontend ESLint and backend compile with all warnings"
	@echo "benchmark    routing benchmark: round-robin vs nearest-neighbor vs Timefold on real roads"
	@echo "bench-api    ping ingest load test and API re-plan latency against the running stack"
	@echo "stop/reset   stop the stack (reset also deletes the database volume)"

setup:
	./scripts/prepare-osrm.sh
	$(GRADLE) :backend:bootJar :simulator:installDist
	cd frontend && npm ci

run:
	$(COMPOSE) up -d --build
	@echo "UI:      http://localhost:3000  (dispatcher@demo.local / dispatch-demo)"
	@echo "API:     $(API)/swagger-ui.html"

stop:
	$(COMPOSE) down

reset:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f backend

simulate:
	$(GRADLE) -q :simulator:run --args="drive --prepare --time-scale $(SPEED) --api $(API)"

test: test-backend test-frontend

test-backend:
	$(GRADLE) test

test-frontend:
	cd frontend && npm test

lint:
	cd frontend && npm run lint
	$(GRADLE) compileJava compileTestJava

benchmark:
	$(COMPOSE) up -d osrm
	until curl -s "http://localhost:5001/route/v1/driving/-87.63,41.88;-87.65,41.9" | grep -q Ok; do sleep 1; done
	$(GRADLE) :backend:benchmark -PbenchmarkArgs="--osrm-url http://localhost:5001"

bench-api:
	COMPOSE="$(COMPOSE)" API=$(API) LEVELS=$(LEVELS) ./scripts/bench-api.sh

sample-data:
	$(GRADLE) -q :backend:sampleData
