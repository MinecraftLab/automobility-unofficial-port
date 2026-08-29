# Modrinth Release - Automobility 0.5.0-unofficial.30 for 26.2

Use this file for the 26.2 upload after the release candidate passes the in-game smoke test.

## Project

- Project: `https://modrinth.com/mod/automobility-unofficial-port`
- Environment: `Client and server, required on both`

## Version Form

- Version type: `beta`
- Version number: `0.5.0-unofficial.30+26.2`
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

- Local JAR: `dist\release-candidate-0.5.0-unofficial.30\automobility-0.5.0-unofficial.30+26.2-fabric.jar`
- Size: `1372366` bytes
- SHA-256: `C06D293D70A2746CB664C94A1058D75FFD5F764B4479794F2DDEBD999E748DD5`
- SHA-512: `D6336787B66C00F358F8D755512C68B970C491093ED937FCB89E78B5E6B3B9FD3B7899C5B1CF8231CAAF66E1994A88B62CCE5DC309CAF6F22FC7E40EA203CE68`

## Version Changelog

Use this exact changelog:

```markdown
Attachment, inventory-name and packaging reliability update for the unofficial
Minecraft Java 26.2 Fabric port.

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
- The rebuilt JAR matched the immutable r8 QA snapshot byte-for-byte.
- Exact-artifact dedicated-server and physical-client testing passed in the complete
  Pumps & Pistons Roadworks stack, including large teleport recovery, driving,
  attachment collision, persistence, restart and clean shutdown.
- No Automobility, mixin, attachment or collision exception occurred.

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
  the `.30` artifact before publishing dependants that require it.
- Confirm Modrinth shows Fabric and only Minecraft 26.2 for this file.
- Confirm Fabric API is required and both Controlify and Momentum are optional.
