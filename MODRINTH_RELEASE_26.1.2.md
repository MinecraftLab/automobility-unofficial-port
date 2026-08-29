# Modrinth Release - Automobility 0.5.0-unofficial.30 for 26.1.x

Use this file for the 26.1.x upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.30+26.1.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.30\automobility-0.5.0-unofficial.30+26.1.2-fabric.jar`
- Size: `1372485` bytes
- SHA-256: `B25957AB1EE7481704E48DEEB33B4AF59998FA73D853F495C9730B893A7CF07A`
- SHA-512: `FE5D876CD7C18CA26F3D902D631536BEFC65143F146E4828A50F07A6F08E7F25ED5C2D1062192C6431F43C62E0B704EDCD433AAC907D65CBD081FBE474104FE5`

## Version Changelog

```markdown
Attachment, inventory-name and packaging reliability update for the unofficial
Minecraft Java 26.1.x Fabric port.

### Fixed

- Rear attachments now realign after large same-dimension teleports without changing
  normal towing, reverse articulation, vertical motion or high-speed Momentum travel.
- Automobility's six registered block items now use their existing localized block
  names instead of displaying raw `item.automobility.*` translation keys.
- Removed stale mixin refmap declarations that referenced an unbundled file.
```

## Verification Record

- Java 25 clean Fabric build: passed on 2026-08-29.
- Fabric test suite: 20 passed, 0 failed, 0 skipped.
- The rebuilt JAR matched the immutable 26.1.2 r8 QA snapshot byte-for-byte.
- Exact-artifact dedicated-server and physical-client testing passed in the complete
  Pumps & Pistons Roadworks stack, including large teleport recovery, driving,
  asymmetric attachment collision, persistence, restart and clean shutdown.
- No Automobility, mixin, attachment or collision exception occurred.

## Before Upload

- Push the `port/26.1.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.
- Use `MODRINTH_DESCRIPTION.md` as the project description.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm the anonymous Modrinth API, CDN download and Maven coordinate all return
  the `.30` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and all three 26.1.x game versions.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
