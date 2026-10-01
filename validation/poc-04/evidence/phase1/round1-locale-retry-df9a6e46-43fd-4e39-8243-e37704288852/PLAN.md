# Round1 narrow locale evidence retry

Preserve e7e372d24c567a41ea66c32d730f0713e70d060ac2f3c178b078d1e9a1b0b751 and all prior reports/evidence unchanged. Current user authorization is limited to replacing unsupported locale SHOW statements with pg_catalog.pg_database provider/locale/encoding evidence; retain V4 regex and behavior cases.

1. Inspect Git/authorities/dedicated daemon; new UUIDv4 run/state and exact source binding.
2. Narrow locale query correction; diff/inventory; focused reason test only. STOP on failure.
3. Only if reason passes, instance uniqueness/restart and teardown negatives. STOP on failure.
4. Only if all focused proofs pass, full Phase1 database/role/ACL/SQL/history/collector proof and check build --rerun-tasks. Exact creator-ledger cleanup and evidence binding. No Git mutation or Phase2.

Provider semantics follow https://www.postgresql.org/docs/17/catalog-pg-database.html: libc uses datcollate/datctype; ICU/builtin use datlocale. Actual behavioral cases decide the reason invariant.
