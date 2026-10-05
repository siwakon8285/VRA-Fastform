-- Admin-only Phase-1 bootstrap, after the accepted POC-01/03 bootstraps.
-- Disposable TEST LOGIN credentials are injected separately at execution time.
-- No capability function or executor table privilege is activated here.
BEGIN;

DO $bootstrap$
DECLARE
    role_name text;
    login_allowed boolean;
    secured_roles constant text[] := ARRAY[
        'vra_sync_executor', 'vra_security_executor', 'vra_telemetry_executor',
        'vra_factor_fixture', 'vra_audit_evidence_reader', 'vra_security_telemetry_observer'
    ];
    executor_roles constant text[] := ARRAY[
        'vra_sync_executor', 'vra_security_executor', 'vra_telemetry_executor'
    ];
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND rolsuper
    ) THEN
        RAISE EXCEPTION 'POC-04 bootstrap requires an administrator';
    END IF;
    IF pg_catalog.to_regrole('vra_owner') IS NULL
       OR pg_catalog.to_regrole('vra_migrator') IS NULL
       OR pg_catalog.to_regnamespace('vra') IS NULL THEN
        RAISE EXCEPTION 'accepted role/schema prerequisites are missing';
    END IF;

    FOREACH role_name IN ARRAY secured_roles LOOP
        login_allowed := NOT (role_name = ANY(executor_roles));
        IF pg_catalog.to_regrole(role_name) IS NULL THEN
            EXECUTE pg_catalog.format(
                'CREATE ROLE %I %s NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
                role_name, CASE WHEN login_allowed THEN 'LOGIN' ELSE 'NOLOGIN' END
            );
        ELSIF EXISTS (
            SELECT 1 FROM pg_catalog.pg_roles
            WHERE rolname = role_name AND (
                rolcanlogin <> login_allowed OR rolinherit OR rolsuper
                OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
            )
        ) THEN
            RAISE NOTICE 'POC-04 role attribute drift detected; applying restrictive attributes to %', role_name;
        END IF;
        EXECUTE pg_catalog.format(
            'ALTER ROLE %I %s NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
            role_name, CASE WHEN login_allowed THEN 'LOGIN' ELSE 'NOLOGIN' END
        );
    END LOOP;

    -- Unexpected privileges are reported, never erased to make inspection pass.
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_auth_members m
        JOIN pg_catalog.pg_roles parent ON parent.oid = m.roleid
        JOIN pg_catalog.pg_roles member ON member.oid = m.member
        WHERE member.rolname = ANY(secured_roles)
           OR (parent.rolname = ANY(secured_roles) AND NOT (
               parent.rolname = ANY(executor_roles) AND member.rolname = 'vra_owner'
           ))
    ) THEN
        RAISE EXCEPTION 'unexpected POC-04 role membership drift';
    END IF;
    IF EXISTS (
        SELECT m.roleid FROM pg_catalog.pg_auth_members m
        JOIN pg_catalog.pg_roles parent ON parent.oid = m.roleid
        WHERE parent.rolname = ANY(executor_roles)
        GROUP BY m.roleid HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'unexpected duplicate executor membership grant';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_database d
        CROSS JOIN LATERAL pg_catalog.aclexplode(d.datacl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE d.datname = pg_catalog.current_database()
          AND r.rolname = ANY(secured_roles)
          AND (a.privilege_type <> 'CONNECT' OR a.is_grantable)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace n
        CROSS JOIN LATERAL pg_catalog.aclexplode(n.nspacl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
          AND (a.privilege_type <> 'USAGE' OR a.is_grantable)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles r
        WHERE r.rolname = ANY(secured_roles)
          AND pg_catalog.has_schema_privilege(r.oid, 'vra', 'CREATE')
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        CROSS JOIN LATERAL pg_catalog.aclexplode(c.relacl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_attribute att
        JOIN pg_catalog.pg_class c ON c.oid = att.attrelid
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        CROSS JOIN LATERAL pg_catalog.aclexplode(att.attacl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        CROSS JOIN LATERAL pg_catalog.aclexplode(p.proacl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        JOIN pg_catalog.pg_roles r ON r.oid = p.proowner
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_type t
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        CROSS JOIN LATERAL pg_catalog.aclexplode(t.typacl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
          AND NOT (r.rolname = 'vra_audit_evidence_reader'
              AND t.typname = 'audit_evidence_row' AND a.privilege_type = 'USAGE'
              AND NOT a.is_grantable)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_database d
        JOIN pg_catalog.pg_roles r ON r.oid = d.datdba
        WHERE d.datname = pg_catalog.current_database() AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace n
        JOIN pg_catalog.pg_roles r ON r.oid = n.nspowner
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_type t
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        JOIN pg_catalog.pg_roles r ON r.oid = t.typowner
        WHERE n.nspname = 'vra' AND r.rolname = ANY(secured_roles)
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_parameter_acl p
        CROSS JOIN LATERAL pg_catalog.aclexplode(p.paracl) a
        JOIN pg_catalog.pg_roles r ON r.oid = a.grantee
        WHERE r.rolname = ANY(secured_roles)
    ) THEN
        RAISE EXCEPTION 'unexpected POC-04 object or parameter privilege drift';
    END IF;

    FOREACH role_name IN ARRAY executor_roles LOOP
        IF EXISTS (
            SELECT 1 FROM pg_catalog.pg_auth_members m
            WHERE m.roleid = pg_catalog.to_regrole(role_name)
              AND (NOT m.set_option OR m.inherit_option OR m.admin_option)
        ) THEN
            RAISE NOTICE 'POC-04 owner membership option drift detected for %', role_name;
        END IF;
        EXECUTE pg_catalog.format(
            'GRANT %I TO vra_owner WITH SET TRUE, INHERIT FALSE, ADMIN FALSE', role_name
        );
    END LOOP;
    EXECUTE pg_catalog.format(
        'GRANT CONNECT ON DATABASE %I TO vra_factor_fixture, vra_audit_evidence_reader, vra_security_telemetry_observer',
        pg_catalog.current_database()
    );
    GRANT USAGE ON SCHEMA vra TO vra_sync_executor, vra_security_executor,
        vra_telemetry_executor, vra_factor_fixture, vra_audit_evidence_reader,
        vra_security_telemetry_observer;

    IF NOT pg_catalog.pg_has_role('vra_migrator', 'vra_owner', 'SET') THEN
        RAISE EXCEPTION 'accepted administrative migrator path is missing';
    END IF;
    FOREACH role_name IN ARRAY executor_roles LOOP
        IF NOT pg_catalog.pg_has_role('vra_migrator', role_name, 'SET') THEN
            RAISE EXCEPTION 'administrative transitive executor path is missing';
        END IF;
        IF EXISTS (
            SELECT 1 FROM pg_catalog.pg_roles r
            WHERE NOT r.rolsuper
              AND r.rolname NOT IN ('vra_owner', 'vra_migrator')
              AND r.rolname <> role_name
              AND pg_catalog.pg_has_role(r.oid, pg_catalog.to_regrole(role_name), 'SET')
        ) THEN
            RAISE EXCEPTION 'ordinary identity has an executor SET path';
        END IF;
    END LOOP;
END
$bootstrap$;

COMMIT;
