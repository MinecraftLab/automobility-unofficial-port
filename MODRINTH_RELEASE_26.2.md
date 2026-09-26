# Modrinth Release - automobility 0.5.0-unofficial.35 for 26.2

> **Target-scoped release record:** Use this file only when its filename target matches
> `minecraft_version` in `gradle.properties` and the default QA gate passes. Other target
> files in this branch are retained only as historical snapshots.

**Status (2026-09-26): PUBLISHED AND ANONYMOUSLY VERIFIED LIMITED BETA.**
Final artifact, unit-test, client integration and dedicated-server gates passed.
Physical-controller, real two-player driving and full-modpack acceptance remain NOT PROVEN.
Previous published .34 and rejected Momentum .10 artifacts remain preserved in staging/history.

## Published identity

- Public version: [0.5.0-unofficial.35+26.2](https://modrinth.com/mod/automobility-unofficial-port/version/FQjdppQu), ID `FQjdppQu`.
- Published UTC: `2026-09-26T11:35:47.081526Z`; anonymous verification UTC: `2026-09-26T11:35:48.0661139Z`.
- Status/type/loader/target: `listed` / `beta` / `fabric` / `26.2`.
- Source: [exact artifact commit](https://git.offchunk.com/Offchunk/automobility-unofficial-port/commit/f4e6023d5001249c3c3339a7d155fccf6fc1f8e2).
- Public metadata and dependencies matched the reviewed four-artifact plan.
  Fresh anonymous CDN download matched the size, SHA-256 and SHA-512 below.
- Local publication evidence: `C:\Users\david\Momentum-10-Release-QA-20260926\automobility-26.2-public-version.json`
  (SHA-256 `814671FED49F557611A59E5997B98A620A1824476D687D25CE8859A9D39291E5`) and
  `automobility-26.2-public-verification.json` (SHA-256 `665F6230371970E52BCEC6567DCAAC232F460C8E52D33A17C306E9830E848D7A`).
- Other Minecraft versions, RIA and project-wide descriptions were not changed.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`
- License: MIT
- Source: `https://git.offchunk.com/Offchunk/automobility-unofficial-port`
- Issues: `https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.35+26.2`
- Version subtitle: `for Minecraft 26.2`
- Loaders: `Fabric`
- Game versions: `26.2`
- Environment: `Client and server, required on both`

## Dependencies

- Fabric API: required
- Momentum for Automobility: Unofficial Port: optional
- RIAutomobility: Unofficial Port: optional
- Controlify (Controller support): optional

Controlify remains compile-only and optional. Tested API families do not imply
support for every future SDK. Momentum must reference the actual public .35 version ID.

## Upload File

- Local JAR: `dist\release-candidate-0.5.0-unofficial.35\automobility-0.5.0-unofficial.35+26.2-fabric.jar`
- Size: `1407814` bytes
- SHA-256: `3C8CBB18F578E59200682275EB6119CFDE9B02DE06C4E41C092E4E3BB65D451D`
- SHA-512: `4213CEA4DE0F45B9BA8B3D436B2A46EBA9B7B172B99E70D74A3BFFE961FBE683913D87C4991FB17232318BE319297574012F5F4D0A6686E9D3C4773003CA1D23`

## Version Changelog

Approved release notes (public version adds the exact source commit and verification scope):

```markdown
Controlify compatibility update for the unofficial Minecraft Java 26.2 Fabric port.

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
  Log: `dist\momentum-10-26.2-verification-20260926\controlify35-final-release-gate.log`, SHA-256 `6384F722BEAF5045C3F01D217008938DB851B34B74BD13F1ECE90F9FACA73484`.
- Exact production JARs in isolated Minecraft clients: all three cases PASS.
  NONE: 2 checks of optional absence/neutral bridge. Controlify 3.0.2: 8 checks of
  seven binding IDs, facade completion and bridge; legacy guides/icons NOT CHECKED.
  Controlify 3.5.3: 94 checks including seven applied guides, four radial icons,
  six synthetic contextual vectors and two resource passes. No physical input was used.
  Evidence root: `C:\Users\david\Momentum-10-Release-QA-20260926\client-26.2\runs`.
  - `fixed-none-probe-001/logs/latest.log`: `10D585DAF953F05CBA15BB45A5E2AEB63BE5F363EEA08278140134314380C039`.
  - `fixed-legacy-probe-001/logs/latest.log`: `9305382CBD10413D7C7BD81740398D8F5FA31AF38AAE25D550024FF3B7A7E45B`.
  - `fixed-modern-probe-001/logs/latest.log`: `BA717953ECBDFC6F41C123B49969D4F9E1AA9D948F07781D4E38B2300B44A30D`.
- Isolated dedicated server with exact core/Momentum/Fabric API, no Controlify or QA
  fixture: startup, status, Sport, reload, sync, restoration to Balanced, save and
  graceful exit 0 PASS. Sync had zero recipients, so network delivery is NOT PROVEN.
  `C:\Users\david\Momentum-10-Release-QA-20260926\servers-fixed\26.2\dedicated-server-pass.log`
  SHA-256: `52659C5200FA105F973C99353BDAAB236B69FFA40B5FA7AA126AEDBBF87DB3D9`.
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
