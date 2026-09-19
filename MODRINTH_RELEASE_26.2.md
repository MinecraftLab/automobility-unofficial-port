# Modrinth Release - Automobility 0.5.0-unofficial.28 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.28+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

Do not add Fabric Loader as a project dependency. The JAR metadata already requires Fabric Loader 0.19.3 or newer.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.28\automobility-0.5.0-unofficial.28+26.2-fabric.jar`
- Size: `1369574` bytes
- SHA-256: `E4077618E3F08A850279E09EDD477B9DE984D96D3D189CF77242B5D75D6AE455`

## Version Changelog

Use the changelog from `MODRINTH_RELEASE_26.1.2.md`, replacing the opening sentence with:

```markdown
Compatibility and translation update for the unofficial Minecraft Java 26.2 Fabric port.
```

## Before Upload

- Complete the 26.2 singleplayer smoke test and, where available, a multiplayer connection test.
- Push the `port/26.2` branch and create the matching Forgejo release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
