# Modrinth Release - Momentum 0.1.11-unofficial.10 for 26.1.x

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Local QA rebuild only (2026-09-19); not an approved Momentum upload.**
The Offchunk contact migration changes the artifact bytes without changing Momentum's
version. The original `.10` candidate remains preserved in
`dist\archive-20260912-pre-offchunk`. Do not replace a published `.10` file
with this local QA rebuild; a separate Momentum release requires its own version
decision and validation.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

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

- Local JAR: `dist\release-candidate-0.1.11-unofficial.10\momentum-0.1.11-unofficial.10+26.1.2-fabric.jar`
- Size: `214524` bytes
- SHA-256: `0AD500B70934CB03BF903723EEC26F96F2BE83621C19743D79F790EA2BB227A2`
- SHA-512: `E7D01693ACA4F6674E0A61035646BB34FCD99E1BA581ACC08E2CE63AB8AE033C3CB653B5DA7058DFB0912BBEAB25A581E61190C2E412A5E6C1E02A0888D2C81B`

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
- Push the `port/26.1.2` branch and create the matching Forgejo release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MOMENTUM_MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API and Automobility are required; YACL, Mod Menu, and Controlify must be optional.
