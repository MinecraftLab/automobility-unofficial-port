# Modrinth Release - Automobility 0.5.0-unofficial.28 for 26.1.x

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.28+26.1.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.28\automobility-0.5.0-unofficial.28+26.1.2-fabric.jar`
- Size: `1369695` bytes
- SHA-256: `DC8DA1C773AC8FF2D9AC700C5EEB81C8CB04515752A5D9D731E36DF4C0883D3F`

## Version Changelog

```markdown
Compatibility and translation update for the unofficial Minecraft Java 26.1.x Fabric port.

### Added
- Added complete Czech, Polish, and Korean translations using current Automobility keys and official Minecraft terminology for vanilla content.
- Added component-specific Auto Mechanic Table input ingredients for addons that require an exact vehicle component variant.

### Fixed
- Completed the Simplified Chinese translation and replaced obsolete MidnightControls keys with the current Controlify bindings.
- Made translation validation require every locale to match the complete English key set while preserving formatting placeholders.
- Preserved component-specific recipe requirements in the mechanic table UI, shift-click matching, and client-server recipe sync.
```

## Before Upload

- Complete the 26.1.2 singleplayer and multiplayer smoke test.
- Push the `main` branch and create the matching Forgejo release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
