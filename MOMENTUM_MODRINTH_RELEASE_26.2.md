# Modrinth Release - Momentum 0.1.11-unofficial.10 for 26.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.10+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Automobility Unofficial Port `0.5.0-unofficial.29`: required
- YetAnotherConfigLib (YACL): optional
- Mod Menu: optional
- Controlify `3.0.2` or newer: optional

YACL enables the in-game options screen. Mod Menu exposes that screen in its mod list when both optional mods are installed.
Controlify adds Momentum's native cruise-control bindings, contextual controller guide, and radial-menu actions.

## Upload File

- Local JAR: `dist\release-candidate-0.1.11-unofficial.10\momentum-0.1.11-unofficial.10+26.2-fabric.jar`
- Size: `214657` bytes
- SHA-256: `810C22307B3F1309342B0E8DE6FAC4D10641677E38EA628552E2AA74D627C939`
- SHA-512: `B6DBF2E18ACDC9202D3C77AC2BFEB563D1FB66C926DE755C9C45702520BF6C444951CE6F5891867555D07EC3C0C492B9DDDA06B36D35AFCA57FAA15D20311BC5`

## Version Changelog

Use this changelog:

```markdown
Native controller-support release for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.

- Added optional native Controlify actions for setting or cancelling cruise control,
  increasing and decreasing its target, and resuming the saved target.
- Added contextual controller-guide hints that only appear while driving and only when
  the corresponding cruise action is currently available.
- Added all four cruise actions to Controlify's radial-menu picker with matching icons.
- Left physical controller buttons unassigned by default so Momentum never takes over
  Minecraft or modded controls; players can map the actions or add them to a radial menu.
- Kept Controlify completely optional: keyboard controls, dedicated servers, and clients
  without Controlify continue to work unchanged.
```

## Before Upload

- Complete the 26.2 client singleplayer and multiplayer smoke tests with the matching Automobility `.31` JAR.
- Push the `port/26.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API and Automobility are required; YACL, Mod Menu, and Controlify must be optional.
