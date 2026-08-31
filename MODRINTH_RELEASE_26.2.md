# Modrinth Release - Automobility 0.5.0-unofficial.29 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.29+26.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.29\automobility-0.5.0-unofficial.29+26.2-fabric.jar`
- Size: `1370245` bytes
- SHA-256: `A442F17C5604BF3BAFDFFE18FB87992EC3353B56B94AB4FF5D0660BCEF095ADA`
- SHA-512: `188BD344DEFE5453B40AB99AF1492E1D2FB6EAAD10A631A6929CE996DDE5F06407CE76A42FE68C93B2FC80836ABCB462FF11F44A25EE654279FEC3E33F50D733`

## Version Changelog

Use this exact changelog:

```markdown
Dedicated-server stability update for the unofficial Minecraft Java 26.2 Fabric port.

### Fixed

- Made automobile hitboxes handle Minecraft 26.2's nullable hard-collision query,
  preventing a dedicated-server crash during natural spawn collision checks with Lithium.
```

## Verification Record

- Java 25 clean Fabric build: passed on 2026-08-25.
- Fabric test suite: 10 passed, 0 failed, 0 skipped.
- Exact-artifact integrated smoke test: passed with the SHA-256 listed above.
- The packaged client loaded Automobility `.29`, Fuel System 1.8, its adapter,
  Momentum and RIAutomobility; an existing world loaded, the automobile and fuel
  assets rendered, and Save & Quit completed with all dimensions saved.
- No Automobility, mixin or collision exception occurred. Authentication warnings
  from the deliberately offline QA identity are expected and unrelated to the mod.

## Before Upload

- Confirm the exact JAR hash above still matches the packaged release candidate.
- Keep the successful 26.2 exact-artifact singleplayer smoke-test evidence with the
  release record. The tested world must load and exit through Save & Quit without an
  Automobility, mixin or collision failure.
- Push the `port/26.2` branch and create the matching GitHub release.
- Upload exactly the JAR and metadata listed above.
- Keep the version as beta while the port still has limited public testing.

## After Upload

- Download the public Modrinth file and compare its SHA-256 with the value above.
- Confirm the anonymous Modrinth API, CDN download and Maven coordinate all return
  the `.29` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
