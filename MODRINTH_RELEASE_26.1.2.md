# Modrinth Release - Automobility 0.5.0-unofficial.29 for 26.1.x

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.29+26.1.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.29\automobility-0.5.0-unofficial.29+26.1.2-fabric.jar`
- Size: `1370366` bytes
- SHA-256: `CC8237F8F3C381E6768678F2AEF46FC9A6CBD8035CC2411F333DFF653FB54260`
- SHA-512: `1CA268765ED47C43D1157448B32EB57BF5C4D9F6E5659C2896315E09309D547E7C966D121C7771925C4AB8A75539DED19B5EEE502449275A2FD73881FEE17093`

## Version Changelog

```markdown
Version-alignment and defensive stability update for the unofficial Minecraft Java 26.1.x Fabric port.

### Fixed
- Made automobile hitboxes answer targetless hard-collision queries safely, keeping the 26.1.2 and 26.2 port lines aligned without dereferencing a missing query target.
```

## Verification Record

- Java 25 clean build: passed on 2026-08-25.
- Fabric test suite: 10 passed, 0 failed, 0 skipped.
- Dedicated Minecraft 26.1.2 full-stack start: passed with Automobility `.29`,
  Momentum `.7`, Pumps & Pistons core and its Automobility integration.
- The server loaded 1,623 recipes and 1,623 advancements, reached `Done`, then
  stopped cleanly with every dimension saved.
- Client and multiplayer smoke tests remain required before upload.

## Before Upload

- Complete the 26.1.2 singleplayer and multiplayer smoke test.
- Push the `port/26.1.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
