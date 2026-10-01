# Health regression diagnosis and gated TEST-only remediation

Preserve prior report786ec48925a56e67e9f9491c3d2922b876d83742758db4a2071de5c1eed197f4 and all history. No repository source change before actual datasource inspection. External Spring TEST listener uses supported callbacks/read-only actual bean getters, pool counters and method stacks; original test still fails on HTTP timeout.

1. Attest isolated daemon and frozen bytes/index; source272 unchanged.
2. Only original failing health test with diagnostics; capture actual datasource settings, healthy probe latencies, outage acquisition stacks/pool state and concurrent liveness. Preserve original test result. No timeout accepted as DOWN.
3. If measured root cause is established, narrow TEST-only acquisition/validation bounds plus exact timing/assertion evidence; separate source-bound run. Keep production config/HTTP2/5/20 deadlines/JDBC1s unchanged.
4. Focused HealthProbePostgresIntegrationTest must pass real200/UP→503/DOWN and independentliveness200/UP. Stop on failed correction. Then fullcheckbuild; thencollector; exactledgercleanup/sourcebinding. NoPhase2/Gitmutations.
