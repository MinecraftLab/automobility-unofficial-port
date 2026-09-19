# Modrinth Release - Momentum 0.1.11-unofficial.4 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/momentum-for-automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.1.11-unofficial.4+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Automobility Unofficial Port `0.5.0-unofficial.26`: required
- YetAnotherConfigLib (YACL): optional
- Mod Menu: optional

YACL enables the in-game options screen. Mod Menu exposes that screen in its mod list when both optional mods are installed.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.26\momentum-0.1.11-unofficial.4+26.2-fabric.jar`
- Size: `92824` bytes
- SHA-256: `951549DFAEF14CF053AB559007EA9679A271B6E820AEAE39223C39AEBF869077`

## Version Changelog

Use the changelog from `MOMENTUM_MODRINTH_RELEASE_26.1.2.md`, replacing the opening sentence with:

```markdown
Patch release for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.
```

## Before Upload

- Complete the 26.2 singleplayer smoke test with the matching Automobility `.26` JAR.
- Push the `port/26.2` branch and create the matching Forgejo release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API and Automobility are required; YACL and Mod Menu must be optional.
