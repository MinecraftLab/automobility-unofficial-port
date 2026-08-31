# Modrinth Release - Momentum 0.1.11-unofficial.7 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.7+26.2`
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

- Local JAR: `momentum\build\libs\momentum-0.1.11-unofficial.7+26.2-fabric.jar`
- Size: `176546` bytes
- SHA-256: `6D21730373F1AAA9248E9E5D86C94EE7BBAEDAA176A43571FFF081E3EC9C5E6B`

## Version Changelog

Use this changelog:

```markdown
Dashboard and localization release for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.

- Moved the complete compact driving cluster to the bottom-left by default while
  retaining custom coordinates and the configurable right-edge anchor.
- Localized the full options screen, notifications and debug HUD in the same 17
  languages as the Automobility port.
- Added strict automated locale validation for key parity, non-empty values and
  formatting placeholders.
- Kept the Fuel System reflection contract and narrow-screen clearance intact.
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
