# Modrinth Release - Momentum 0.1.11-unofficial.9 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.9+26.2`
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

- Local JAR: `momentum\build\libs\momentum-0.1.11-unofficial.9+26.2-fabric.jar`
- Size: `205905` bytes
- SHA-256: `6AB18E0E503F6F4AF5B3BB29616B68C25EE9A308D725E0B9B1B4EE6AA1B36797`

## Version Changelog

Use this changelog:

```markdown
Cruise-control and live server-management release for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.

- Added remappable cruise target controls: Increase and Decrease change the set speed
  by 5 km/h by default, while Resume restores the last target after cancellation.
- Kept the saved target after braking, drifting, reversing or an impact, and cleared it
  safely when the driver leaves the vehicle.
- Added concise overlay feedback and distinct confirmation tones for engage, adjustment,
  resume, cancellation and unavailable actions.
- Added `/momentum status`, `reload`, `sync` and `preset touring|balanced|sport` commands.
  Administrative changes synchronize immediately with every compatible connected client.
- Added cruise enable and target-step settings to the in-game options screen, read-only
  when controlled by a multiplayer server.
- Localized the new controls, messages, settings and commands across all 17 supported languages.
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
