# Modrinth Release - Momentum 0.1.11-unofficial.8 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.8+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Automobility Unofficial Port `0.5.0-unofficial.29`: required
- YetAnotherConfigLib (YACL): optional
- Mod Menu: optional

YACL enables the in-game options screen. Mod Menu exposes that screen in its mod list when both optional mods are installed.

## Upload File

- Local JAR: `momentum\build\libs\momentum-0.1.11-unofficial.8+26.2-fabric.jar`
- Size: `186382` bytes
- SHA-256: `6F52224A3C8B8C7EF991B848FEFC542C24B697FE614A8D054D469A52A7FF4C73`

## Version Changelog

Use this changelog:

```markdown
Driving-tune and multiplayer-options release for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.

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

- Complete the 26.2 client singleplayer and multiplayer smoke tests with the matching Automobility `.29` JAR.
- Push the `port/26.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API and Automobility are required; YACL and Mod Menu must be optional.
