# Automobility 26.3 candidate

> **Target-scoped release record:** Only for minecraft_version=26.3.

Status: UNPUBLISHED CANDIDATE. Client and multiplayer acceptance pending.

- Version number: `0.5.0-unofficial.34+26.3`
- Version type: `beta`
- Game versions: `26.3`
- Loaders: `Fabric`
- Environment: client and server, required on both
- Source: https://git.offchunk.com/Offchunk/automobility-unofficial-port
- Issues: https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues
- Required dependency: Fabric API
- Optional dependency: matching Momentum and RIAutomobility builds
- Controlify: optional adapter retained, runtime support for 26.3 not proven

## Upload file

- Local JAR: `dist\release-candidate-0.5.0-unofficial.34\automobility-0.5.0-unofficial.34+26.3-fabric.jar`
- Size: `1396419` bytes
- SHA-256: `D0731665902A1F9DA02AB1CC467AE48443B64E0A5FD0629711B35F79B90792DC`
- SHA-512: `E4794C74738F10AB08D8E23B12B9A515B2791E2926E8A3563B1A3BD16DD2CE9FAAAD631357E20945C79E1BCD3C2F3DF89F7D15531FA055669A4433E63910C7BD`

## Changes and evidence

Ports .34 animal exclusion, security, persistence and position-sync fixes to
Minecraft 26.3. Migrates SDL input, rendering signatures, block APIs and the paver's
registry-driven shovel transformations. No artwork changes.
Client QA additionally fixes invisible mechanic category titles and assembler
statistics text; compiled-render-call regression tests cover both corrections.

See PORTING_26.3.md for exact tests and limitations. Build/server checks do not prove
visual rendering, real-player driving, multiplayer or addon acceptance. Never
replace the already-published 26.1.x/26.2 files with this target-specific JAR.
