# Corrected health Phase1 gated execution

Preserve diagnosis FAIL and all previous reports. Only HealthProbePostgresIntegrationTest changed: TEST-only actual Hikari bean acquisition2000ms/validation1000ms configured before HikariPool initialization, original HTTP2/5/20 deadlines and JDBC1s, actual runtime identity/safe times and concurrent liveness. First live-setter candidate c4415d925530b1c27602f93e8815a41592d5e03230bd18af9882b0900ebba2cb was never executed: static Hikari bytecode showed cached pool timeouts and restore-path gap; revised before-initialization configuration avoids those mechanisms. Production unchanged.

1. gitdiffcheck/source/preflight. Only focused entireHealthclass;STOPifFAIL. Real200UPbefore/readiness503DOWNafter/liveness200UPwithbounded measuredlatency. No timeout accepted asDOWN.
2. OnlyifPASS: completecheckbuild--rerun-tasks;STOPifFAIL;zero skips/errors.
3. Same corrected-byte raw fresh/populatedV4 catalogs/history/ACL/state andteardown negatives as necessary, then pendingcollectorOS/mount/SQL proof guardedbysame-runruntimePASS.
4. Exactcurrentcreator-ledgercleanup, runwideUUIDv5/sysmap/source/history/pins/V1V2V3integrity, secret scan andfinalreport. StatusNO/Phase2NOTAUTH/POC04S0S15NV;noGitmutations.
