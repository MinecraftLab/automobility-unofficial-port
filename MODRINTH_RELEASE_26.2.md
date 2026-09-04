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
- Size: `1391675` bytes
- SHA-256: `7419A34BED47726B200DD4A3D2059AB59DBC27C1D8A9B2CDFBB6335F3D6E2A87`
- SHA-512: `86D95F17FBBA6DD415F627E984F437124B6104DC97DAB7F1F897275D0703EF5677F7B977ADF466C3CD0CD0485A839C879C9205CDA44C5CABE58B9BE5CF1C94AA`

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
- Data packs now reject non-finite or non-positive frame weights and wheel radii.
- Temporarily missing add-on frame, wheel and engine IDs survive save/reload instead of
  being silently replaced by Automobility's empty fallback.
- Dismounting checks more positions and poses for collision before using the above-car fallback.

### Changed

- Added one-command clean build, test, JAR-content and release-hash verification.
- Bundled project notices and the MIT license notice for the embedded Obj 0.4.0 library.
```

## Verification Record

- Java 25 clean merged-source build: passed on 2026-09-04.
- Fabric and Momentum unit suites: 83 passed, 0 failed, 0 skipped.
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
