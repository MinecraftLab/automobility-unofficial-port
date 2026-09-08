# Modrinth Release - Automobility 0.5.0-unofficial.32 for 26.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.32+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

Do not add Fabric Loader as a project dependency. The JAR metadata already requires Fabric Loader 0.19.3 or newer.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.32\automobility-0.5.0-unofficial.32+26.2-fabric.jar`
- Size: `1396746` bytes
- SHA-256: `341AD0241C792C73DABB13EBDCFD4150DE5B73014BD6393A8C65CB15E7A1BE05`
- SHA-512: `55E676ABCBC0A69CB1A3DE30C0546DF5DA40EEB71B6E2C33B7507A52A8A86E2F315E1AA1DDCB8F497DDA993ED27896F0526DE935E904BE218B62535D055D5EC6`

## Version Changelog

Use this exact changelog:

```markdown
Teleport-rendering, security, data-safety and release-verification update for the unofficial
Minecraft Java 26.2 Fabric port.

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

- Automobile body models now stay aligned with their entities and interaction hitboxes
  after same-dimension teleports and authoritative relocation corrections. The existing
  relative suspension height and body tilt are preserved, then the destination terrain is
  refreshed once without changing ordinary driving packets or requiring a reconnect.
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

- Focused Java 25 Fabric source suite: 67 passed, 0 failed, 0 skipped on 2026-09-08.
  This compiles the client hook and covers displacement translation, stale-sync recovery,
  one-shot refresh policy and packaged mixin registration; it is not a live packet test.
- Java 25 clean build and full unit suites: passed on 2026-09-08; 101 tests,
  0 failed, 0 skipped (Fabric 67; Momentum 34).
- Fresh `.32` production JAR metadata, contents, licenses and unsafe-file checks:
  passed on 2026-09-08.
- Default exact staged-JAR and release-record hash gate: **PENDING**.
- Clean-checkout CI reproduction of the committed `.32` hashes: **PENDING**.
- Exact `.32` dedicated-server startup and clean shutdown: **NOT RUN**.
- Exact `.32` entity persistence, missing-add-on NBT and autonomous spawn-protection
  regression: **NOT RUN**.
- Exact `.32` headless addon startup and released RIA `.6` codec contract: **NOT RUN**.
- The passed `.31` server and codec evidence from 2026-09-04 is historical only and does
  not validate the new `.32` bytes.
- Exact `.32` single-client parked-car teleport regression, first without `/data merge`
  and then with the observed fuel/addon/shader sequence: **NOT RUN**.
- Exact `.32` locally driven and remotely observed teleport, normal driving interpolation,
  horizontal relocation, dimension transfer and reconnect smoke: **NOT RUN**.
- Exact-artifact two-client ecosystem smoke: **NOT RUN**.
- Ecosystem dependency metadata for the `.32` candidate: **PENDING**.
- Publication remains blocked until every applicable pending item above passes.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` and confirm the final gate matches the
  exact `.32` size and hashes above.
- First update and verify the Pumps & Pistons/Roadworks Automobility dependency range.
  Run separate core-addon and Roadworks lanes; do not combine Roadworks with Fuel System.
- Reproduce the original parked-car vertical teleport first without `/data merge`; the
  model must remain visible immediately without reconnecting. Then repeat with the full
  fuel/addon/shader sequence and cover horizontal and dimension relocation.
- Complete the exact-JAR two-client smoke on a dedicated server with Automobility,
  Momentum, RIA, Autoradio and the compatible Roadworks build. Cover driving, observer
  tracking, run-over damage, attachment menu removal, item drops, dismounting, persistence,
  reconnect, restart and clean shutdown.
- Confirm the canonical source and issue URLs are publicly reachable before upload; do not
  replace the flagged account with a duplicate repository or account.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm the anonymous Modrinth API, CDN download and Maven coordinate all return
  the `.32` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
