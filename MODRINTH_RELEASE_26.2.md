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
- Size: `1390305` bytes
- SHA-256: `375EB2200BFB94C773F418398BF79B1F14660462AAA90F4B625C2B71CF05DD79`
- SHA-512: `600DF0B8AEA39C88E1165885D1B9D25424EBCDFFB771415CE7FC97C24B4E011AD5A346928FED19EB91118F7E73572E9896721872DD8B38FA3D5A4D68AC5CCD78`

## Version Changelog

Use this exact changelog:

```markdown
Security, data-safety and release-verification update for the unofficial
Minecraft Java 26.2 Fabric port.

### Security

- The legacy vehicle-state packet keeps its existing layout for ecosystem compatibility,
  but the server now accepts only bounded driving input and computes gameplay speed and
  collision impact itself.
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
- Fabric and Momentum unit suites: 77 passed, 0 failed, 0 skipped.
- Exact `.31` JAR metadata, license and release-record hash gate: passed on 2026-09-04.
- Exact `.31` JAR dedicated-server startup and clean shutdown: passed on 2026-09-04.
  The Windows host emitted an OSHI/Perflib system-information diagnostic, but there was
  no Automobility exception or crash report and the server reached `Done` normally.
- Exact-artifact two-client ecosystem smoke: **NOT RUN**.
- Publication remains blocked until every pending item above passes.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` and confirm the final gate matches the
  exact size and hashes above.
- Complete the exact-JAR two-client smoke on a dedicated server with the Automobility,
  Momentum, RIA, Autoradio and Pumps & Pistons Roadworks stack. Cover driving, observer
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
