# Modrinth Release - Automobility 0.5.0-unofficial.34 for 26.1.2

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
- Version number: `0.5.0-unofficial.34+26.1.2`
- Version subtitle: `for Minecraft 26.1.x`
- Loaders: `Fabric`
- Game versions: `26.1`, `26.1.1`, `26.1.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Controlify (Controller support): optional
- Momentum for Automobility: Unofficial Port: optional

The loader minimum remains in JAR metadata; do not add it as a project dependency.
Pumps & Pistons/Roadworks candidates pinned to `.33` require separately verified
dependency updates before combining them with `.34`.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.34\automobility-0.5.0-unofficial.34+26.1.2-fabric.jar`
- Size: `1399122` bytes
- SHA-256: `220B68FAC9419D29FE96640C64B08D9B098F3B22B42F983EBF97212F83526537`
- SHA-512: `AFE08EBC705FA42330AD2352F52854F1DFCFD200B9DC68FE24052B2EFC564A98CC3FE9DC6A37E12A707E55A7074EC3EFB22EE7C2C19174D60ADD4B54A1C3C9C5`

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

- Base: `.33` at `5d91f9c22c615f723e9180ea59f8470ed977dbb6`.
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
