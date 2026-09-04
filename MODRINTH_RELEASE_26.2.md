# Modrinth Release - Automobility 0.5.0-unofficial.31 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.31+26.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.31\automobility-0.5.0-unofficial.31+26.2-fabric.jar`
- Size: `1392323` bytes
- SHA-256: `41B6AD4857CB8D4882EC5060EFD2E6DC6A4A6A41AF80B90544A62F2DEEB30FA1`
- SHA-512: `A1EF52A4276438C48BDAFBFBA916354C07D0F8033E470AAF1734CEBAAC76249DD355E8090BECF056CED626E525379A2417046AA3ACF3549607E72DA0B5193977`

## Version Changelog

Use this exact changelog:

```markdown
Security, data-safety and release-verification update for the unofficial
Minecraft Java 26.2 Fabric port.

### Security

- The legacy vehicle-state packet keeps its existing layout for ecosystem compatibility,
  but the server now accepts only bounded, fresh driving input and computes gameplay speed.
- Run-over damage now requires real server-observed movement and caps collision impact to
  that movement, so stale acceleration cannot damage entities around a stationary car.
- Attachment detail requests now require an exact payload, a tracked nearby automobile,
  and a bounded per-player request budget.
- Player-driven harvester and plow actions now respect the world border and vanilla spawn
  protection while preserving Automobility's explicit allow-block behavior elsewhere.

### Fixed

- Removed the unconditional forced velocity update previously sent for every automobile tick.
- Raw automobile items without custom component data now place a playable default car.
- Removing a chest, ender-chest or banner attachment now invalidates its open menu and
  safely transfers any dropped item stacks on the server.
- Data-pack frame weights, engine torque and speed, wheel size and grip, wheel radius,
  and per-wheel display scale now use bounded finite ranges that keep vehicle physics safe.
  Accepted ranges are respectively `0.01-1.5`, `0-3`, `0-4`, `0-4`, `0-1`,
  `0.25-64` pixels and `0.01-2`.
- Temporarily missing add-on frame, wheel and engine IDs survive save/reload instead of
  being silently replaced by Automobility's empty fallback.
- Dismounting checks more positions and poses for collision before using the above-car fallback.

### Changed

- Added one-command clean build, test, JAR-content and release-hash verification.
- Bundled project notices and the MIT license notice for the embedded Obj 0.4.0 library.
```

## Verification Record

- Java 25 clean merged-source build: passed on 2026-09-04.
- Fabric and Momentum unit suites: 89 passed, 0 failed, 0 skipped
  (Fabric 55; Momentum 34).
- Exact `.31` JAR metadata, license and release-record hash gate: passed on 2026-09-04.
- Exact `.31` JAR dedicated-server startup and clean shutdown: passed on 2026-09-04.
  The Windows host emitted an OSHI/Perflib system-information diagnostic, but there was
  no Automobility exception or crash report and the server reached `Done` normally.
- Exact `.31` entity save, full server restart and UUID reload: passed on 2026-09-04.
- Headless addon startup with Momentum `.10`, RIA `.6` and immutable Autoradio `0.1.0`:
  passed on 2026-09-04. This does not cover client mixins or gameplay.
- Ecosystem metadata preflight: Momentum `.10`, RIA `.6` and Autoradio `0.1.0` accept
  Automobility `.31`; current Pumps & Pistons/Roadworks `dev.19` pins exact `.30`, and
  Fuel System `1.8.0` pins exact `.29`. Pumps & Pistons also declares a Fuel System conflict.
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

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm the anonymous Modrinth API, CDN download and Maven coordinate all return
  the `.31` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
