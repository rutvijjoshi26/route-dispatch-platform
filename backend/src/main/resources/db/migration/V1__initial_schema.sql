CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE depot (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    location    geography(Point, 4326) NOT NULL
);

CREATE TABLE vehicle (
    id          BIGSERIAL PRIMARY KEY,
    depot_id    BIGINT       NOT NULL REFERENCES depot (id),
    name        VARCHAR(60)  NOT NULL UNIQUE,
    capacity    INT          NOT NULL CHECK (capacity > 0),
    shift_start TIME         NOT NULL,
    shift_end   TIME         NOT NULL,
    CHECK (shift_end > shift_start)
);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('DISPATCHER', 'DRIVER')),
    vehicle_id    BIGINT REFERENCES vehicle (id),
    CHECK (role <> 'DRIVER' OR vehicle_id IS NOT NULL)
);

CREATE TABLE dispatch_plan (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    service_date    DATE         NOT NULL,
    depot_id        BIGINT       NOT NULL REFERENCES depot (id),
    status          VARCHAR(20)  NOT NULL,
    version         INT          NOT NULL DEFAULT 0,
    score           VARCHAR(80),
    travel_seconds  BIGINT,
    distance_meters BIGINT,
    dispatched_at   TIMESTAMPTZ,
    clock_origin    TIME,
    time_scale      DOUBLE PRECISION,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE stop (
    id                BIGSERIAL PRIMARY KEY,
    plan_id           BIGINT       NOT NULL REFERENCES dispatch_plan (id) ON DELETE CASCADE,
    order_ref         VARCHAR(40)  NOT NULL,
    customer_name     VARCHAR(120) NOT NULL,
    address           VARCHAR(255) NOT NULL,
    location          geography(Point, 4326) NOT NULL,
    window_start      TIME         NOT NULL,
    window_end        TIME         NOT NULL,
    service_seconds   INT          NOT NULL CHECK (service_seconds >= 0),
    demand            INT          NOT NULL CHECK (demand > 0),
    package_size      VARCHAR(10)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    vehicle_id        BIGINT REFERENCES vehicle (id),
    sequence          INT,
    planned_arrival   TIME,
    planned_departure TIME,
    committed_eta     TIME,
    en_route_at       TIME,
    actual_arrival    TIME,
    actual_departure  TIME,
    inbound_leg       geography(LineString, 4326),
    added_mid_shift   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_version       BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (plan_id, order_ref),
    CHECK (window_end > window_start)
);
CREATE INDEX idx_stop_plan_vehicle ON stop (plan_id, vehicle_id, sequence);
CREATE INDEX idx_stop_location ON stop USING GIST (location);

CREATE TABLE route (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT NOT NULL REFERENCES dispatch_plan (id) ON DELETE CASCADE,
    vehicle_id      BIGINT NOT NULL REFERENCES vehicle (id),
    geometry        geography(LineString, 4326),
    return_leg      geography(LineString, 4326),
    distance_meters BIGINT NOT NULL,
    travel_seconds  BIGINT NOT NULL,
    depart_depot    TIME   NOT NULL,
    return_depot    TIME   NOT NULL,
    load_units      INT    NOT NULL,
    UNIQUE (plan_id, vehicle_id)
);

CREATE TABLE plan_revision (
    id               BIGSERIAL PRIMARY KEY,
    plan_id          BIGINT      NOT NULL REFERENCES dispatch_plan (id) ON DELETE CASCADE,
    version          INT         NOT NULL,
    reason           VARCHAR(30) NOT NULL,
    detail           VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    solve_millis     BIGINT      NOT NULL,
    total_millis     BIGINT      NOT NULL,
    score            VARCHAR(80) NOT NULL,
    travel_seconds   BIGINT      NOT NULL,
    distance_meters  BIGINT      NOT NULL,
    pinned_stops     INT         NOT NULL,
    changed_vehicles INT         NOT NULL,
    UNIQUE (plan_id, version)
);

CREATE TABLE stop_event (
    event_id    UUID PRIMARY KEY,
    stop_id     BIGINT      NOT NULL REFERENCES stop (id) ON DELETE CASCADE,
    vehicle_id  BIGINT      NOT NULL REFERENCES vehicle (id),
    type        VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome     VARCHAR(20) NOT NULL,
    note        VARCHAR(255)
);
CREATE INDEX idx_stop_event_stop ON stop_event (stop_id, occurred_at);

CREATE TABLE gps_ping (
    ping_id     UUID PRIMARY KEY,
    plan_id     BIGINT      NOT NULL REFERENCES dispatch_plan (id) ON DELETE CASCADE,
    vehicle_id  BIGINT      NOT NULL REFERENCES vehicle (id),
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    location    geography(Point, 4326) NOT NULL,
    speed_mps   REAL,
    heading_deg REAL
);
CREATE INDEX idx_gps_ping_vehicle_time ON gps_ping (plan_id, vehicle_id, recorded_at);

CREATE TABLE vehicle_position (
    plan_id     BIGINT      NOT NULL REFERENCES dispatch_plan (id) ON DELETE CASCADE,
    vehicle_id  BIGINT      NOT NULL REFERENCES vehicle (id),
    recorded_at TIMESTAMPTZ NOT NULL,
    location    geography(Point, 4326) NOT NULL,
    speed_mps   REAL,
    heading_deg REAL,
    PRIMARY KEY (plan_id, vehicle_id)
);
CREATE INDEX idx_vehicle_position_location ON vehicle_position USING GIST (location);
