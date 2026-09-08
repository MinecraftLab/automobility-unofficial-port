# Modrinth Release - Automobility 0.5.0-unofficial.33 for 26.1.x

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status: RUNTIME VERIFICATION IN PROGRESS - NOT APPROVED FOR UPLOAD.**

This is an unpublished candidate record. Use it for the 26.1.x upload only after
the exact JAR passes every applicable runtime and ecosystem gate below.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.33+26.1.2`
- Version subtitle: `for Minecraft 26.1.x`
- Loaders: `Fabric`
- Game versions: `26.1`, `26.1.1`, `26.1.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

Do not add Fabric Loader as a project dependency. The JAR metadata already requires Fabric Loader 0.19.2 or newer.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.33\automobility-0.5.0-unofficial.33+26.1.2-fabric.jar`
- Size: `1398967` bytes
- SHA-256: `2736C27143FD20E19911B2DCB0359D9EBF4AAC18C207F11C3C608BEA74E1BCC7`
- SHA-512: `05F373FA5DAEF43A068E425C60DBC39DE2AAACD65912FC893AD604A3D569FA91FEE17404D0323AA39450623609082836B9F58488C1020BC0ADAEFD7FE973680A`

## Version Changelog

```markdown
Position-sync rendering, security, data-safety and release-verification update for the unofficial
Minecraft Java 26.1.x Fabric port.

### Security

- The legacy vehicle-state packet keeps its existing layout for ecosystem compatibility,
  but the server now accepts only bounded, fresh driving input and computes gameplay speed.
- Run-over damage now requires real server-observed movement and caps collision impact to
  that movement, so stale acceleration cannot damage entities around a stationary car.
- Attachment detail requests now require an exact payload, a tracked nearby automobile,
  and a bounded per-player request budget.
- Harvester and plow actions, including autopilot and mob-controlled vehicles, now respect
  the world border and vanilla spawn protection while preserving Automobility's explicit
  allow-block behavior outside protected areas.

### Fixed

- Added reconciliation of cached automobile suspension displacement after the
  absolute position-sync packets used for large observer relocations and the teleport
  packets used for riding players. The relative-update path preserves normal movement
  interpolation while rebasing the client-local suspension sample and scheduling a
  destination-terrain refresh.
- Removed the unconditional forced velocity update previously sent for every automobile tick.
- Raw automobile items without custom component data now place a playable default car.
- Attachment menus now invalidate when their attachment is removed or replaced, the car
  is gone, the player changes dimension or leaves range. Chest and banner inventories
  transfer or drop their original stacks safely only on the server.
- Data-pack frame weights, engine torque and speed, wheel size and grip, wheel radius,
  and per-wheel display scale now use bounded finite ranges that keep vehicle physics safe.
  Accepted ranges are respectively `0.01-2.5`, `0-3`, `0-4`, `0-4`, `0-1`,
  `0.25-64` pixels and `0.01-2`.
- Supported RIAutomobility `.6` components remain registry-sync compatible, while extreme
  data-pack combinations can no longer reverse acceleration/steering or amplify velocity.
- Temporarily missing add-on frame, wheel and engine IDs survive save/reload instead of
  being silently replaced by Automobility's empty fallback.
- Temporarily missing add-on front/rear attachment NBT, including nested inventory data,
  survives save/reload until the attachment is explicitly replaced.
- Dismounting checks more positions and poses for collision before using the above-car fallback.

### Changed

- Added one-command clean build, test, JAR-content and release-hash verification.
- Bundled project notices and the MIT license notice for the embedded Obj 0.4.0 library.
```

## Verification Record

- Java 25 clean build and full unit suites: passed on 2026-09-08; 107 tests,
  0 failed, 0 skipped (Fabric 73; Momentum 34). The suite covers displacement
  translation, stale-sync recovery, packet-handler registration and hard/soft
  classification boundaries; it is not a live packet test.
- Fresh `.33` production JAR metadata, contents, licenses and unsafe-file checks:
  passed on 2026-09-08.
- Candidate preparation clean rebuild and staging: passed on 2026-09-08; the built
  and staged JARs matched the size, SHA-256 and SHA-512 recorded above.
- Default exact staged-JAR and committed release-record hash gate: passed on
  2026-09-08; a second clean build reproduced the recorded size, SHA-256 and
  SHA-512 for both Automobility and Momentum.
- CI-mode clean rebuild against the committed `.33` release record: **PENDING**.
- Exact `.33` full-stack dedicated-server startup and normal save/shutdown:
  passed on 2026-09-08, including an exclusive world-lock check after exit.
- Exact `.33` entity persistence, missing-add-on NBT and autonomous spawn-protection
  regression: **NOT RUN**.
- Exact `.33` headless addon startup and released RIA `.6` codec contract: **NOT RUN**.
- The passed `.31` server and codec evidence from 2026-09-04 is historical only and does
  not validate the new `.33` bytes.
- Exact `.32` 26.2 runtime regression: **FAILED**. Its model remained at the previous
  altitude after an empty parked automobile was teleported upward by 17 blocks. The
  failed candidate is recorded in `FAILED_RELEASE_CANDIDATES.md` and is not releasable.
- Exact `.33` full-stack single-client parked-car vertical relocation: passed on
  2026-09-08 for `+17Y` absolute sync and a following `+6Y` relative update. The
  automobile model remained visible at the authoritative destination.
- Exact `.33` occupied/local-driver `-23Y` relocation: passed on 2026-09-08; the
  model and rider remained together without rollback. A single vanilla
  moved-too-quickly warning was recorded.
- Exact `.33` empty-car server-driven coasting: passed on 2026-09-08; the automobile
  moved, settled and remained visible without changing fuel during the tested impulse.
- The first same-client disconnect run ended in a native Microsoft OpenJDK 25.0.1+8
  G1 crash about 24 seconds after disconnect. `hs_err_pid9004.log` has SHA-256
  `A3475B9CFB3B0EA7E221C5DCB83E15CB1B67DD6CD1593F50A5FFF89B233013A9` and reports
  `g1HeapRegionManager.cpp:55` on the VM Periodic Task Thread with only native
  `jvm.dll` frames; it contains no Java or Automobility frame. This matches Microsoft
  OpenJDK issue `https://github.com/microsoft/openjdk/issues/677` and is classified
  as an environmental JVM/G1 incident, not a demonstrated `.33` failure.
- Controlled same-stack rerun with only
  `-XX:+UnlockDiagnosticVMOptions -XX:-G1UseTimeBasedHeapSizing`: disconnect, a
  5-minute 39-second idle period and reconnect passed on 2026-09-08. The automobile
  remained visible and retained fuel `44701`; Autoradio Next/progress interaction also
  passed. The server stayed healthy for 13 minutes 44 seconds, beyond the original
  13-minute crash point, produced no new `hs_err`, and then completed `save-all flush`
  and a normal stop with exit code 0. Immutable client evidence
  `creative-ecosystem-1\auto33-g1-mitigated-reconnect-exit.log` has SHA-256
  `B7CFF8A3DC2E2BF14F07B16D4D9FE657F5CC8CBBCF6178CE66D0EFE20CBECBFE`;
  immutable server evidence
  `server-run-creative-1\auto33-g1-mitigated-reconnect-exit.log` has SHA-256
  `14F02A9C651A6B4574F53CDF55CB7D60B656099B8899E1A347C627C2DAB6385B`.
  Both are preserved under
  `C:\Users\david\RosenvikCanary\graphics-fixes-20260908`.
  The diagnostic flag was applied only to the isolated QA server; Automobility
  neither requires nor sets JVM flags, and no normal player profile was changed.
- Exact `.33` held-key/manual driving continuity: **NOT PROVEN**.
- Exact `.33` small relocation as seen by a third-party client and the complete
  exact-artifact two-client ecosystem smoke: **NOT PROVEN**.
- Exact `.33` horizontal relocation and dimension-transfer smoke: **NOT RUN**.
- Ecosystem dependency metadata for the `.33` candidate: **PENDING**.
- Publication remains blocked until every applicable pending item above passes.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` and confirm the final gate matches the
  exact `.33` size and hashes above.
- First update and verify the Pumps & Pistons/Roadworks Automobility dependency range.
  Run separate core-addon and Roadworks lanes; do not combine Roadworks with Fuel System.
- Reproduce the original parked-car vertical teleport first without `/data merge`; the
  model must remain visible immediately without reconnecting. Then repeat with the full
  fuel/addon/shader sequence. Cover `+17Y` absolute sync, a smaller relative teleport,
  occupied/local-driver and third-party-observer paths, ordinary driving, unmanned
  coasting, horizontal relocation, dimension transfer and reconnect.
- Complete the exact-JAR two-client smoke on a dedicated server with Automobility,
  Momentum, RIA, Autoradio and the compatible Roadworks build. Cover driving, observer
  tracking, run-over damage, attachment menu removal, item drops, dismounting, persistence,
  reconnect, restart and clean shutdown.
- Confirm the canonical source and issue URLs are publicly reachable before upload; do not
  replace the flagged account with a duplicate repository or account.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm the anonymous Modrinth API, CDN download and Maven coordinate all return
  the `.33` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
