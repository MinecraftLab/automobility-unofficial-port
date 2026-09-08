# Failed release candidates

This record preserves exact identities and failure evidence for candidates that
must never be uploaded, reused as dependencies, renamed or represented as released
builds. Evidence only; do not publish or reuse as another version.

## 0.5.0-unofficial.32 - 2026-09-08

### Minecraft 26.2 - runtime rejected

- Source commit: `aa26250e9ac1c26a00d4dfbaf9b2b8d27738ea1c`
- JAR: `automobility-0.5.0-unofficial.32+26.2-fabric.jar`
- Size: `1396746` bytes
- SHA-256: `341AD0241C792C73DABB13EBDCFD4150DE5B73014BD6393A8C65CB15E7A1BE05`
- SHA-512: `55E676ABCBC0A69CB1A3DE30C0546DF5DA40EEB71B6E2C33B7507A52A8A86E2F315E1AA1DDCB8F497DDA993ED27896F0526DE935E904BE218B62535D055D5EC6`
- Result: `REJECTED-RUNTIME`

The candidate hooked `ClientboundTeleportEntityPacket`, but a live dedicated-server
test proved that a 17-block vertical teleport of an empty tracked automobile used
`ClientboundEntityPositionSyncPacket`. The entity and interaction hitbox moved to
the destination while the rendered body remained at its cached previous altitude.
Returning the entity to that altitude made the model visible again without reconnecting.

Preserved evidence:

- Server log: `C:\Users\david\RosenvikCanary\graphics-fixes-20260908\server-run-survival-1\auto32-teleport-runtime-failure.log`
- Client log: `C:\Users\david\RosenvikCanary\graphics-fixes-20260908\survival-ecosystem-1\auto32-teleport-runtime-failure.log`
- Bytecode diagnosis: `C:\Users\david\RosenvikCanary\graphics-fixes-20260908\auto32-packet-diagnosis\READONLY-EVIDENCE.md`
- Screenshot: `C:\Users\david\RosenvikCanary\graphics-fixes-20260908\survival-ecosystem-1\screenshots\2026-09-08_12.28.24.png`

### Minecraft 26.1.2 - superseded without runtime test

- Source commit: `ac427d1080c245364105ccb2abbe4187fbbde453`
- JAR: `automobility-0.5.0-unofficial.32+26.1.2-fabric.jar`
- Size: `1396768` bytes
- SHA-256: `4243DB608D280275ED56A68B6D15626E36811AD942A003C005D288E9B980AEDE`
- SHA-512: `75EAAD2C07C3B263D4BE426AFAB20C0B1804EA096D9E476350DBB5B6D1BBCAEE0B565F0CFB6ECF755EA2CE491E7D5B3245B9948301DB2A407C7A4988AD0C31A1`
- Result: `SUPERSEDED-UNTESTED`

The same incomplete packet strategy was used in this target. It was superseded
without spending a runtime lane after the exact 26.2 candidate failed.

### Immutable archive

Both JARs and verified complete-history Git bundles are preserved under:

`C:\Users\david\Rosenvik-Automobility-backups\automobility-32-runtime-rejected-20260908`

The archive's `MANIFEST.md` is the canonical inventory. The archive must remain
read-only evidence; `.33` or later must be built from their dedicated worktrees.
