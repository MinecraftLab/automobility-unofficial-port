# Modrinth Release - Momentum 0.1.11-unofficial.4 for 26.1.x

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.4+26.1.2`
- Version subtitle: `for Minecraft 26.1.x`
- Loaders: `Fabric`
- Game versions: `26.1`, `26.1.1`, `26.1.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Automobility Unofficial Port `0.5.0-unofficial.26`: required
- YetAnotherConfigLib (YACL): optional
- Mod Menu: optional

YACL enables the in-game options screen. Mod Menu exposes that screen in its mod list when both optional mods are installed.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.26\momentum-0.1.11-unofficial.4+26.1.2-fabric.jar`
- Size: `92740` bytes
- SHA-256: `9732D1C462708A6A15C656F3B2B7B9CA0464A7D25D65DC438A12881EEC01569F`

## Version Changelog

```markdown
Patch release for the unofficial Minecraft Java 26.1.x Fabric port of Momentum for Automobility.

### Added
- Added working optional Mod Menu integration when YACL is installed.
- Added backward-compatible server-to-client gameplay configuration sync so vehicle physics use the server's settings in multiplayer.
- Added remote brake and drift input sync for automobiles tracked by other clients.
- Added Controlify brake and drift input through Automobility's existing controller bindings.

### Fixed
- Restricted client input to the automobile driven by the local player instead of applying static brake or drift state to every loaded automobile.
- Ignored raw brake and drift keys while a chat, menu, or other screen is open.
- Made automatic drift direction deterministic across the client and server and used automobile-specific ground detection on slopes.
- Recovered safely from malformed configuration files, validated unsafe numeric values, and wrote configuration updates atomically.
- Reset key-state synchronization between multiplayer connections and stopped rendering a filled speed bar at zero speed.

### Changed
- Moved the default speed HUD position to the upper-right corner for new configurations.
- Kept HUD, camera, and key preferences client-side while treating movement, steering, drift, and cruise behavior as server-authoritative in multiplayer.
```

## Before Upload

- Complete the 26.1.2 singleplayer and multiplayer smoke test with the matching Automobility `.26` JAR.
- Push the `main` branch and create the matching Forgejo release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MOMENTUM_MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API and Automobility are required; YACL and Mod Menu must be optional.
