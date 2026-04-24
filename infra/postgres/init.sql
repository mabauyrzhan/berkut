-- Enables TimescaleDB before Flyway runs. Tables (gps_points, events, etc.)
-- are created by Flyway migrations inside processor-service.
CREATE EXTENSION IF NOT EXISTS timescaledb;
