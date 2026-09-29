\set ON_ERROR_STOP on
-- Admin-run POC bootstrap, after validation/poc-01/db/bootstrap.sql.
-- Supply the five *_password psql variables; no production credentials here.
BEGIN;
SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='vra_outbox_worker') AS role_exists \gset
\if :role_exists
ALTER ROLE vra_outbox_worker LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\else
CREATE ROLE vra_outbox_worker LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif
ALTER ROLE vra_outbox_worker PASSWORD :'outbox_worker_password';
SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='vra_reconciliation_worker') AS role_exists \gset
\if :role_exists
ALTER ROLE vra_reconciliation_worker LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\else
CREATE ROLE vra_reconciliation_worker LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif
ALTER ROLE vra_reconciliation_worker PASSWORD :'reconciliation_worker_password';
SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='vra_async_operator') AS role_exists \gset
\if :role_exists
ALTER ROLE vra_async_operator LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\else
CREATE ROLE vra_async_operator LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif
ALTER ROLE vra_async_operator PASSWORD :'async_operator_password';
SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='vra_projection_rebuilder') AS role_exists \gset
\if :role_exists
ALTER ROLE vra_projection_rebuilder LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\else
CREATE ROLE vra_projection_rebuilder LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif
ALTER ROLE vra_projection_rebuilder PASSWORD :'projection_rebuilder_password';
SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='vra_async_observer') AS role_exists \gset
\if :role_exists
ALTER ROLE vra_async_observer LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\else
CREATE ROLE vra_async_observer LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif
ALTER ROLE vra_async_observer PASSWORD :'async_observer_password';
SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='vra_async_executor') AS role_exists \gset
\if :role_exists
ALTER ROLE vra_async_executor NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\else
CREATE ROLE vra_async_executor NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\endif
GRANT vra_async_executor TO vra_owner WITH SET TRUE, INHERIT FALSE, ADMIN FALSE;
REVOKE ALL ON SCHEMA vra FROM PUBLIC;
GRANT CONNECT ON DATABASE vra_poc01 TO vra_outbox_worker, vra_reconciliation_worker,
    vra_async_operator, vra_projection_rebuilder, vra_async_observer;
GRANT USAGE ON SCHEMA vra TO vra_outbox_worker, vra_reconciliation_worker,
    vra_async_operator, vra_projection_rebuilder, vra_async_observer, vra_async_executor;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles r
        WHERE r.rolname IN ('vra_runtime','vra_outbox_worker','vra_reconciliation_worker',
            'vra_async_operator','vra_projection_rebuilder','vra_async_observer')
        AND (pg_catalog.pg_has_role(r.oid,'vra_owner','SET')
             OR pg_catalog.pg_has_role(r.oid,'vra_async_executor','SET'))
    ) THEN
        RAISE EXCEPTION 'ordinary workload has administrative SET path';
    END IF;
END $$;
COMMIT;
