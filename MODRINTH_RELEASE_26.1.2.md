# Modrinth Release - automobility 0.5.0-unofficial.35 for 26.1.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status (2026-09-26): READY FOR LIMITED BETA; NOT YET PUBLISHED.**
Final artifact, unit-test, client integration and dedicated-server gates passed.
Physical-controller, real two-player driving and full-modpack acceptance remain NOT PROVEN.
Previous published .34 and rejected Momentum .10 artifacts remain preserved in staging/history.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.35+26.1.2`
- Version subtitle: `for Minecraft 26.1.2`
- Loaders: `Fabric`
- Game versions: `26.1.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Momentum for Automobility: Unofficial Port: optional
- RIAutomobility: Unofficial Port: optional
- Controlify (Controller support): optional

Controlify remains compile-only and optional. Tested API families do not imply
support for every future SDK. Momentum must reference the actual public .35 version ID.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.35\automobility-0.5.0-unofficial.35+26.1.2-fabric.jar`
- Size: `1407840` bytes
- SHA-256: `9115F8A3787BD44F7D637624E8A1FC4C52EE92FF6B1D0A56BBC74C023CBB97C5`
- SHA-512: `504C19CC17397CAEDD2576CD385796C128B278968F5EF0514CFE24682CE4B8E5A83255AAE742AC73D6075DC49EFCA86B5DE6CC26736A7C5C21373FBEA7AE1AF3`

## Version Changelog

Approved release notes (public version adds the exact source commit and verification scope):

```markdown
Controlify compatibility update for the unofficial Minecraft Java 26.1.2 Fabric port.

- Restores optional controller-guide registration with Controlify 3.5.3 while preserving the legacy 3.0.2 API.
- Preserves existing binding IDs and player mappings; unknown APIs retain keyboard controls with a warning.
- Retains .34 animal-boarding, input validation and persistence fixes; no vehicle physics changes are intended.

Install on client and server. Controlify remains optional; SDKs and QA fixtures are not bundled.
Beta: physical controller input, full multiplayer driving and complete modpack acceptance are not proven.
```

## Verification Record

- Earlier 107-test results describe previous .34 / legacy-only Momentum .10 bytes, not this candidate.
- Initial 26.2 + Controlify 3.5.3 runtime reproduced both broken pre-init callbacks.
  Historical RED evidence: `C:\Users\david\Momentum-10-Release-QA-20260926\CONTROLIFY_3_5_3_BLOCKER.md`.
- Final Java 25 default gate with `-Rebuild` (without preparation bypass): PASS,
  119 tests (82 Fabric + 37 Momentum), 0 failures and 0 skips. Clean rebuild matches
  the canonical JAR and this record in size, SHA-256 and SHA-512.
  Log: `dist\controlify-fix-20260926\final-release-gate.log`, SHA-256 `1C87BD217D55CDF9C4CA0E2BB077014EC50843C24623B6B0A49C2381829251E6`.
- Exact production JARs in isolated Minecraft clients: all three cases PASS.
  NONE: 2 checks of optional absence/neutral bridge. Controlify 3.0.2: 8 checks of
  seven binding IDs, facade completion and bridge; legacy guides/icons NOT CHECKED.
  Controlify 3.5.3: 94 checks including seven applied guides, four radial icons,
  six synthetic contextual vectors and two resource passes. No physical input was used.
  Evidence root: `C:\Users\david\Momentum-10-Release-QA-20260926\client-26.1.2\runs`.
  - `fixed-none-probe-001/logs/latest.log`: `5B800FFBF9F39E6EF9B9603D383D59A2E7FA33CC9BF791C3F44DC0D741FB9A5F`.
  - `fixed-legacy-probe-001/logs/latest.log`: `439D374E96557ACDD97ABA18DCDA16F92BC8CABA4907F89423768A746FC82F3E`.
  - `fixed-modern-probe-001/logs/latest.log`: `74B846DD41BC1A769733BD1D86B4A8B5B84C93FD80468DC8F9078F90A2D6CB99`.
- Isolated dedicated server with exact core/Momentum/Fabric API, no Controlify or QA
  fixture: startup, status, Sport, reload, sync, restoration to Balanced, save and
  graceful exit 0 PASS. Sync had zero recipients, so network delivery is NOT PROVEN.
  `C:\Users\david\Momentum-10-Release-QA-20260926\servers-fixed\26.1.2\dedicated-server-pass.log`
  SHA-256: `9792A42A45812BAFCC68714BDFB8A232113E4E771785C7E3755BD4E273CC43A6`.
- Production packaging guard excludes QA classes and bundled/required Controlify SDK.
  Guard regression fixtures: 10 checks across both targets PASS.
- Controlify 3.5.3 emitted an SDL3 native 3.4.12 versus Java binding 3.4.14 warning
  on Windows; SDL initialization completed, but no physical controllers were present.
  Offline-account/Realms and Windows system-information warnings remain in QA logs.
- Physical controller input and two-real-player/full-modpack acceptance: NOT PROVEN.
- User approved coordinated core/Momentum repair and publication on 2026-09-26;
  authorization is not evidence that unrun player checks passed.

## Publication Gate

Run `tools\qa\Test-Automobility.ps1 -Rebuild` with the final staged artifacts.
Publish core first, then Momentum with its actual public dependency ID. Verify anonymous
listed metadata, a fresh CDN download, size and both hashes. Never overwrite a published version.
