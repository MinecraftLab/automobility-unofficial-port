# Automobility 26.3 candidate

> **Target-scoped release record:** Only for minecraft_version=26.3.

Status: MANUALLY ACCEPTED BETA / FINAL PUBLICATION CHECKS (2026-09-19).
The user accepted the local-driver turning correction in the 4859E013 candidate.
The newer 9DF5ACCF candidate retains that correction and also fixes remote-vehicle
interpolation; its real-client packet-handler regression passed 46 checks.
The user then confirmed "ano vše ok" after being asked to test cruise control,
G seat switching and cargo in the final candidate. Those requested checks are
accepted. Two-physical-player, exhaustive crafting/persistence and controller
coverage remain NOT PROVEN, not inferred from this confirmation.
See PORTING_26.3.md for the exact evidence and remaining limits.

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
- Size: `1401851` bytes
- SHA-256: `9DF5ACCF8E744D4904BFEE6CFD953AE6ADB3F80032AFCCECB3E02B798A4BBE1C`
- SHA-512: `C020FE5B6E448F48A7E4FEA466058DE1D366F7156BD2AB6C0BF41D9F5396E17095275F88E61C2BF956D3C34ABA7C49EDB0A1B22BA4EA01F92F4652403E4ECF37`

The older D0731665 GUI candidate is retained in
`dist/previous-candidates/gui-before-final-release-20260919`; the 4859E013 steering
candidate also remains historical evidence, not an upload target. The accepted
interpolation candidate was copied byte-for-byte to the canonical release path.
Do not upload the separate test-only client-driving QA JAR.

## Changes and evidence

Ports .34 animal exclusion, security, persistence and position-sync fixes to
Minecraft 26.3. Migrates SDL input, rendering signatures, block APIs and the paver's
registry-driven shovel transformations. No artwork changes.
Client QA additionally fixes invisible mechanic category titles and assembler
statistics text; compiled-render-call regression tests cover both corrections.
Fixes stepped local-driver camera rotation with the 26.3 passenger tick order.
Restores remote-vehicle smoothing through the new 26.3 interpolation API while
preserving hard/relative teleports and local-driver authority.

Final technical evidence: 101 core + 43 Momentum tests, all passing; packaged
server animal/core/ecosystem/RIA and ExamplePack checks passing; 46 real-client
packet-handler checks passing. The client-only fixture is absent from production.

See PORTING_26.3.md for exact tests and limitations. Build/server checks do not prove
visual rendering, real-player driving, multiplayer or addon acceptance. Never
replace the already-published 26.1.x/26.2 files with this target-specific JAR.
