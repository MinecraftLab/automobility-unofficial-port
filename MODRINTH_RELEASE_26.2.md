# Modrinth Release - Automobility 0.5.0-unofficial.34 for 26.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status: CANDIDATE IN VERIFICATION - NOT APPROVED FOR UPLOAD.**

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://github.com/nickynicolaus/automobility-unofficial-port`
- Issues: `https://github.com/nickynicolaus/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.34+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

The loader minimum remains in JAR metadata; do not add it as a project dependency.
Pumps & Pistons/Roadworks candidates pinned to `.33` require separately verified
dependency updates before combining them with `.34`.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.34\automobility-0.5.0-unofficial.34+26.2-fabric.jar`
- Size: `1399096` bytes
- SHA-256: `E8C825FD7851EAFA001F5836548265321703496D76C246267162342F808F8D21`
- SHA-512: `78A4DC236FBA31CE84C1C4332D0CD654EE85313AEFEEEAEF86ECF561DB0C75CC2A5073C545224FE394790763AD7B5BB457F53FE4C9BC68429E74E12D37075A8E`

## Version Changelog

```markdown
Animals no longer occupy automobile seats.

- Prevented animals from boarding automatically or through normal mounting, including
  rear passenger seats, aquatic animals and bats.
- Animals already seated in an older save dismount safely on the next server tick.
- Players and non-animal mobs keep their existing boarding behavior.

Includes the unpublished .31 security/data-safety fixes and .33 position-sync
rendering repairs described in CHANGELOG.md.
```

## Verification Record

- Base: `.33` at `dea66f3b7b36a23ce3358dfdd483814bdffd6f41`.
- New `.34` clean build, unit suites and JAR integrity: **PENDING**.
- Exact staged-JAR and release-record hash gate: **PENDING**.
- Dedicated-server animal boarding, existing animal dismount and non-animal
  passenger control: **PASS**, 22 scenario groups on 2026-09-12, including 17
  animal types and NBT save/load with mixed passengers. Server stopped with exit 0.
  See `tools/qa/ANIMAL_BOARDING_QA.md` for coverage and reproduction.
- Prior `.33` runtime results apply only to its original hashes, retained in the
  `.33` worktree and Git history.
- Held-key/manual driving, two-client observer, horizontal/dimension transfer,
  full persistence/missing-addon/spawn-protection and ecosystem acceptance:
  **NOT PROVEN for .34**.
- Public source/issues availability and explicit upload approval remain release gates.

## Before Upload

- Run `tools\qa\Test-Automobility.ps1 -Rebuild` against the final staged artifacts.
- Verify the target-specific ecosystem dependency ranges and player acceptance.
- Confirm canonical source and issue URLs are anonymously reachable; preserve the
  existing account recovery process.
- Upload only after approval, using the exact JAR and metadata in this record.

## After Upload

- Download the public file and compare size, SHA-256 and SHA-512.
- Verify anonymous Modrinth metadata, CDN and Maven availability before dependants.
