# Modrinth Release - Momentum 0.1.11-unofficial.5 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.5+26.2`
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

- Local JAR: `momentum\build\libs\momentum-0.1.11-unofficial.5+26.2-fabric.jar`
- Candidate size: `101623` bytes
- Candidate SHA-256: `8ABE9D2B19BE08C9A74D65B7FDA057313F9EBB8E45D417B7C160816AD4186BAC`

Recompute both values from the final canonical artifact after the in-game smoke test;
do not publish if either differs without a documented rebuild.

## Version Changelog

Use this changelog:

```markdown
HUD polish release for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.

- Added a compact unified driving dashboard with exact speed, graphical km/h unit,
  visible speed/boost segments and a persistent cruise target.
- Kept every visual inside the declared dashboard panel for reliable companion-HUD placement.
- Retained the previous configurable bar-and-text HUD as an optional legacy layout.
```

## Before Upload

- Complete the 26.2 singleplayer smoke test with the matching Automobility `.29` JAR.
- Push the `port/26.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API and Automobility are required; YACL and Mod Menu must be optional.
