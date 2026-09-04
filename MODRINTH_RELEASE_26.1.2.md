# Modrinth Release - Automobility 0.5.0-unofficial.31 for 26.1.x

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.31+26.1.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.31\automobility-0.5.0-unofficial.31+26.1.2-fabric.jar`
- Size: `1393873` bytes
- SHA-256: `9D3BAB2BB0F3588335FF4341B1B28785B5002DB042B83CA5F75006774800F916`
- SHA-512: `146C114E84931728C3C084B8329EEFF8B7CC29E8F2A80C6946C60A818058A0CC00D8C0DF35F882D2037C225F724277A8ABFF09DFFD68137321FCF9A1BD75AF5B`

## Version Changelog

```markdown
Security, data-safety and release-verification update for the unofficial
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

- Java 25 clean merged-source build: passed on 2026-09-04.
- Fabric and Momentum unit suites: 94 passed, 0 failed, 0 skipped
  (Fabric 60; Momentum 34).
- Clean-checkout CI build verification reproduced both module JARs and matched their
  committed size, SHA-256 and SHA-512 records on 2026-09-04.
- Exact `.31` JAR metadata, license and release-record hash gate: passed on 2026-09-04.
- Exact `.31` JAR dedicated-server startup and clean shutdown: passed on 2026-09-04;
  the server reached `Done` with no errors, exceptions or crash report.
- Exact `.31` entity save, full server restart and UUID reload, including opaque
  nested NBT for missing front and rear add-on attachments: passed on 2026-09-04.
- Exact `.31` autonomous autopilot+paver integration: four dirt blocks inside
  dedicated-server spawn protection remained unchanged while the matching four-block
  probe outside protection became dirt path; passed on 2026-09-04.
- Headless addon startup with Momentum `.10`, RIA `.6` and immutable Autoradio `0.1.0`:
  passed on 2026-09-04. This does not cover client mixins or gameplay.
- Released RIA `.6` source-contract test: all 13 frame definitions and both wheel
  definitions round-tripped through the exact `.31` `DIRECT_CODEC` with
  `RegistryOps`/`NbtOps`; passed on both Minecraft targets on 2026-09-04.
- Ecosystem metadata preflight: Momentum `.10`, RIA `.6` and Autoradio `0.1.0` accept
  Automobility `.31`; current Pumps & Pistons/Roadworks `dev.19` pins exact `.30`.
  Pumps & Pistons also declares a Fuel System conflict.
- Exact-artifact two-client ecosystem smoke: **NOT RUN**.
- Publication remains blocked until every pending item above passes.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` and confirm the final gate matches the
  exact size and hashes above.
- First update and verify the Pumps & Pistons/Roadworks Automobility dependency range.
  Run separate core-addon and Roadworks lanes; do not combine Roadworks with Fuel System.
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
  the `.31` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
