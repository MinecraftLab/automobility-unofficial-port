# Modrinth Release - Momentum 0.1.11-unofficial.10 for 26.1.x

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.10+26.1.2`
- Version subtitle: `for Minecraft 26.1.x`
- Loaders: `Fabric`
- Game versions: `26.1`, `26.1.1`, `26.1.2`
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

- Local JAR: `momentum\build\libs\momentum-0.1.11-unofficial.10+26.1.2-fabric.jar`
- Size: `213962` bytes
- SHA-256: `4BC9FAE42DB483C8402D3A1334244B30B23074CA4A793AA112E16DE97279DC31`

## Version Changelog

```markdown
Native controller-support release for the unofficial Minecraft Java 26.1.x Fabric port of Momentum for Automobility.

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

- Complete the 26.1.2 client singleplayer and multiplayer smoke tests with the matching Automobility `.31` JAR.
- Push the `port/26.1.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MOMENTUM_MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API and Automobility are required; YACL, Mod Menu, and Controlify must be optional.
