-- Metadata for vehicles (hydrated from vehicles.registry Kafka topic)
CREATE TABLE vehicles (
    device_id     VARCHAR(64) PRIMARY KEY,
    license_plate VARCHAR(32) NOT NULL,
    driver_name   VARCHAR(128) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GPS time-series. Hypertable partitions by `time` into daily chunks. No PK:
-- duplicates are unlikely (ms-granular timestamp + device_id), chasing uniqueness
-- would slow bulk inserts to no practical benefit.
CREATE TABLE gps_points (
    time            TIMESTAMPTZ NOT NULL,
    device_id       VARCHAR(64) NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    speed_kmh       DOUBLE PRECISION NOT NULL,
    heading_degrees DOUBLE PRECISION NOT NULL
);
SELECT create_hypertable('gps_points', 'time', chunk_time_interval => INTERVAL '1 day');
CREATE INDEX ix_gps_device_time ON gps_points (device_id, time DESC);

-- Event stream. Composite PK (time, event_id): TimescaleDB requires the time column
-- to be part of any unique index on a hypertable. ON CONFLICT DO NOTHING gives us
-- idempotent replay from Kafka.
CREATE TABLE events (
    time       TIMESTAMPTZ NOT NULL,
    event_id   UUID NOT NULL,
    device_id  VARCHAR(64) NOT NULL,
    type       VARCHAR(32) NOT NULL,
    severity   VARCHAR(16) NOT NULL,
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL,
    speed_kmh  DOUBLE PRECISION NOT NULL,
    group_id   UUID,
    metadata   JSONB,
    PRIMARY KEY (time, event_id)
);
SELECT create_hypertable('events', 'time', chunk_time_interval => INTERVAL '1 day');
CREATE INDEX ix_events_device_time        ON events (device_id, time DESC);
CREATE INDEX ix_events_type_severity_time ON events (type, severity, time DESC);
CREATE INDEX ix_events_group              ON events (group_id, time DESC) WHERE group_id IS NOT NULL;

-- Grouping metadata for windowed-dedup output (populated in day 4 by dedup-service).
CREATE TABLE event_groups (
    group_id     UUID PRIMARY KEY,
    device_id    VARCHAR(64) NOT NULL,
    type         VARCHAR(32) NOT NULL,
    severity     VARCHAR(16) NOT NULL,
    first_seen   TIMESTAMPTZ NOT NULL,
    last_seen    TIMESTAMPTZ NOT NULL,
    event_count  INTEGER NOT NULL DEFAULT 1,
    closed_at    TIMESTAMPTZ
);
CREATE INDEX ix_event_groups_device_lastseen ON event_groups (device_id, last_seen DESC);
CREATE INDEX ix_event_groups_open            ON event_groups (last_seen DESC) WHERE closed_at IS NULL;
