\set ON_ERROR_STOP on

SELECT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'vra_owner'
) AS owner_exists \gset

\if :owner_exists
    ALTER ROLE vra_owner
        NOLOGIN
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        NOBYPASSRLS;
\else
    CREATE ROLE vra_owner
        NOLOGIN
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        NOBYPASSRLS;
\endif

SELECT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'vra_migrator'
) AS migrator_exists \gset

\if :migrator_exists
    ALTER ROLE vra_migrator
        LOGIN
        NOINHERIT
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        NOBYPASSRLS;
\else
    CREATE ROLE vra_migrator
        LOGIN
        NOINHERIT
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        NOBYPASSRLS;
\endif

ALTER ROLE vra_migrator PASSWORD :'migrator_password';

SELECT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'vra_runtime'
) AS runtime_exists \gset

\if :runtime_exists
    ALTER ROLE vra_runtime
        LOGIN
        NOINHERIT
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        NOBYPASSRLS;
\else
    CREATE ROLE vra_runtime
        LOGIN
        NOINHERIT
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOREPLICATION
        NOBYPASSRLS;
\endif

ALTER ROLE vra_runtime PASSWORD :'runtime_password';

GRANT vra_owner TO vra_migrator
    WITH ADMIN FALSE, INHERIT FALSE, SET TRUE;

REVOKE ALL ON DATABASE vra_poc01 FROM PUBLIC;
GRANT CONNECT ON DATABASE vra_poc01 TO vra_migrator, vra_runtime;

CREATE SCHEMA IF NOT EXISTS vra AUTHORIZATION vra_owner;
ALTER SCHEMA vra OWNER TO vra_owner;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA vra FROM PUBLIC;
GRANT USAGE ON SCHEMA vra TO vra_runtime;
