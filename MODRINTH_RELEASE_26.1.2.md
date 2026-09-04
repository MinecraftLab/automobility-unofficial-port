# Modrinth Release - Automobility 0.5.0-unofficial.31 for 26.1.x

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
- Size: `1390332` bytes
- SHA-256: `C37CFC669A5301401800CD1C2B3590801A647ED285FB046117B88511DF9BDFC4`
- SHA-512: `95F32EA1574D51EC54F8C8E8481EF79CBCBEE5C169B46DEE525069B973D8B4B92F4BD3A97E2D7E4274A2F0F9938F89A9E7A45C2BAA2836B1E627F4FAD918FAC0`

## Version Changelog

```markdown
Security, data-safety and release-verification update for the unofficial
Minecraft Java 26.1.x Fabric port.

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
- Exact-artifact dedicated-server startup and two-client ecosystem smoke: **NOT RUN**.
- Publication remains blocked until every pending item above passes.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` and confirm the final gate matches the
  exact size and hashes above.
- Test the exact JAR on a dedicated server and with two clients in the Automobility,
  Momentum, RIA, Autoradio and Pumps & Pistons Roadworks stack. Cover driving, observer
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
