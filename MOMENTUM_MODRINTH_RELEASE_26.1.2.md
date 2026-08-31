# Modrinth Release - Momentum 0.1.11-unofficial.8 for 26.1.x

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.8+26.1.2`
- Version subtitle: `for Minecraft 26.1.x`
- Loaders: `Fabric`
- Game versions: `26.1`, `26.1.1`, `26.1.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Automobility Unofficial Port `0.5.0-unofficial.29`: required
- YetAnotherConfigLib (YACL): optional
- Mod Menu: optional

YACL enables the in-game options screen. Mod Menu exposes that screen in its mod list when both optional mods are installed.

## Upload File

- Local JAR: `momentum\build\libs\momentum-0.1.11-unofficial.8+26.1.2-fabric.jar`
- Size: `186453` bytes
- SHA-256: `282ADBDB8358D83C682A26618E344DD459AFD0BAC4A6729A1E065CCD3E109C2E`

## Version Changelog

```markdown
Driving-tune and multiplayer-options release for the unofficial Minecraft Java 26.1.x Fabric port of Momentum for Automobility.

- Added Touring, Balanced and Sport driving tunes, plus a Custom tune that preserves
  and restores hand-tuned movement and steering values.
- Extended coasting across every named tune while keeping Touring the most relaxed
  and Sport the firmest.
- Kept drift, camera, HUD, sound, key bindings and cruise control independent from
  driving-tune changes.
- Displayed the server's real gameplay tune and settings as read-only in multiplayer,
  while camera and HUD options remain personal.
- Added a one-time migration to Balanced that does not overwrite later manual choices
  or unrelated preferences.
```

## Before Upload

- Complete the 26.1.2 client singleplayer and multiplayer smoke tests with the matching Automobility `.29` JAR.
- Push the `port/26.1.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MOMENTUM_MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API and Automobility are required; YACL and Mod Menu must be optional.
