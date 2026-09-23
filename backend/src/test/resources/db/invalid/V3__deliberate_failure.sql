-- Test fixture only: PostgreSQL must roll back DDL and fail application startup.
CREATE TABLE public.migration_failure_probe (id BIGINT PRIMARY KEY);
SELECT 1 / 0;
