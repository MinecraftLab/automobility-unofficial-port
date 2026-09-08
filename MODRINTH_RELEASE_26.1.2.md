# Modrinth Release - Automobility 0.5.0-unofficial.33 for 26.1.x

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status: PRE-RUNTIME - NOT APPROVED FOR UPLOAD.**

This is a pre-runtime candidate record. Use it for the 26.1.x upload only after
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
- Default exact staged-JAR and committed release-record hash gate: **PENDING**.
- CI-mode clean rebuild against the committed `.33` release record: **PENDING**.
- Exact `.33` dedicated-server startup and clean shutdown: **NOT RUN**.
- Exact `.33` entity persistence, missing-add-on NBT and autonomous spawn-protection
  regression: **NOT RUN**.
- Exact `.33` headless addon startup and released RIA `.6` codec contract: **NOT RUN**.
- The passed `.31` server and codec evidence from 2026-09-04 is historical only and does
  not validate the new `.33` bytes.
- Exact `.32` 26.2 runtime regression: **FAILED**. Its model remained at the previous
  altitude after an empty parked automobile was teleported upward by 17 blocks. The
  failed candidate is recorded in `FAILED_RELEASE_CANDIDATES.md` and is not releasable.
- Exact `.33` single-client parked-car teleport regression, first without `/data merge`
  and then with the observed fuel/addon/shader sequence: **NOT RUN**.
- Exact `.33` locally driven and remotely observed teleport, small and large relocation,
  normal driving interpolation, empty-car coasting,
  horizontal relocation, dimension transfer and reconnect smoke: **NOT RUN**.
- Exact-artifact two-client ecosystem smoke: **NOT RUN**.
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
