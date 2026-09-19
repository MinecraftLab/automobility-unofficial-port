# Minecraft 26.3 core port - local candidate

Status on 2026-09-19: **FINAL CANDIDATE MANUALLY ACCEPTED / PUBLICATION CHECKS IN PROGRESS**.
Branch `port/26.3`, initially based on Automobility `.34` commit `36f0044`.
No normal Minecraft profile or production server was changed. The initial core
port has now been extended to Momentum and tested with a separate RIAutomobility
26.3 worktree. The final requested manual checks are now accepted; the publication
receipt below must be verified before claiming a public release.

## Build and dependencies

- Minecraft Java 26.3 (stable), Java 25.
- Fabric Loader 0.19.5; Fabric API 0.161.0+26.3.
- Loom 1.17.21; Gradle wrapper 9.6.0.
- Source and issue links use `https://git.offchunk.com/Offchunk/automobility-unofficial-port`.

Run from this worktree in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Users\david\.jdks\temurin-25-20260619175454\jdk-25.0.3+9'
.\gradlew.bat :fabric:build :fabric:animalBoardingQaJar --no-daemon --console=plain --max-workers=2
```

This exact core build passed: **73 tests, 0 failures/errors/skips**, plus Loom access
widener validation. The opt-in QA JAR is separate from the production JAR.

The follow-up `tools/qa/Test-Automobility.ps1 -Rebuild` passed the complete clean
build/release-integrity gate after the text fixes: **118 tests (75 core + 43 Momentum), zero failures or
skips**. Target-specific release records and staged JARs are verified by exact
size, SHA-256 and SHA-512. RIA's initial separate clean build passed 26 tests; its
user-approved G-default follow-up passed **27 tests**, giving **145 unit tests**
across the final stack. See `PORTING_MOMENTUM_26.3.md` for its SDL
key migration and retained legacy configuration format.

Controlify remains optional, compile-only and not bundled. Its existing 26.2 SDK
compiled the compatibility adapter, but no published 26.3 Controlify build was
available in the checked publisher metadata. Controller support is **NOT PROVEN**;
do not install a 26.2 Controlify JAR into a 26.3 game or claim it compatible.
Other addons (Autoradio, Roadworks, Fuel System, etc.) are outside this port stage.

## Source migration

- Replaced removed block codecs and GLFW mouse constants with 26.3 APIs.
- Preserved model rotation order using `PoseStack.rotate`; migrated model submit
  calls to the new signature without changing UV or outline arguments.
- Migrated the entity velocity-sync flag and position-path packet endpoint while
  retaining the existing dismount and teleport reconciliation policies.
- Replaced the removed `ShovelItem.FLATTENABLES` mixin with the world's data-driven
  shovel state transformations. Paver air-above checks and the enclosing
  permission/write boundary are retained. This does not implement arbitrary
  transformer side effects such as loot, tool consumption or special chest logic.
- Updated renamed piston reaction and registry-holder sound references.
- Production animal admission, collision pickup filter and saved-passenger
  cleanup behavior are unchanged from `.34`.
- Real client QA additionally found a transparent category title: 26.3 text
  extraction rejects zero-alpha `0x00FFFFFF`, so the title now uses opaque ARGB.
  Assembler stats also had a pre-existing color/light integer-argument mix-up;
  the renderer now passes `lightCoords, 0xFFFFFFFF, 0, 0`. Two compiled-bytecode
  regression tests were first observed failing against the old calls. The fix
  does not change frame geometry, textures, HUD defaults or vehicle physics.

## Dedicated-server evidence

Isolated server: `C:\Users\david\Automobility-26.3-Port-QA`, bound only to
`127.0.0.1:25663`, Fabric Loader 0.19.5, exact production candidate, matching Fabric
API and opt-in fixture. No players, Controlify or other addons were installed.
The server saved and stopped normally with exit code 0; its listener is closed.

Retained successful log: `animal-boarding-port263-pass.log` in that server directory.
SHA-256: `DB370528B4C9C8D85E727FB84961C76FD086997C8577FBCA1633217EDB2A271D`.

Required success markers observed:

```text
AUTO34_ANIMAL_BOARDING_PASS checks=22
AUTO263_PORT_SMOKE_PASS checks=5
```

The 22 groups cover the real passenger admission/collision/forced-save-load tests
documented in `tools/qa/ANIMAL_BOARDING_QA.md`. Five additional checks prove grass
flattens to dirt path, computing that result does not write the world, stone stays
unchanged, grass blocked above stays unchanged, and mechanic recipes are present
in the loaded 26.3 registry-backed server recipe manager.

The first two runs exposed a fixture prerequisite, not a production animal-filter
failure. In 26.3, `LivingEntity.isPushable` rejects entities outside an entity-ticking
chunk. `getChunk(0, 0)` in `SERVER_STARTED` loaded terrain but did not make a valid
collision-pickup scenario. The fixture now requests a forced chunk, waits through
`END_SERVER_TICK` for actual entity-ticking (maximum 200 ticks), and explicitly
asserts both pig and villager are pushable candidates before testing pickup. It
releases the chunk ticket before stopping. Production pickup code was not changed
to bypass vanilla's predicate. Failed logs are preserved as
`animal-boarding-first-failure.log` and `animal-boarding-visibility-diagnostic.log`.
The first disposable world's missing flat-generator settings were also corrected;
the successful fresh-world run has explicit flat layers.

The Windows OSHI system-info diagnostics appear before mod startup; they are not
substitutes for the explicit fixture markers. Exit 0 by itself is never a pass.

## Candidate identity and remaining gates

Local candidate:
`dist/port-candidate-26.3/automobility-0.5.0-unofficial.34+26.3-fabric.jar`.
Embedded mod version remains `0.5.0-unofficial.34`, with exact Minecraft dependency
`26.3`; the target suffix distinguishes this unpublished port artifact.

- Bytes: `1396419`
- SHA-256: `D0731665902A1F9DA02AB1CC467AE48443B64E0A5FD0629711B35F79B90792DC`
- SHA-512: `E4794C74738F10AB08D8E23B12B9A515B2791E2926E8A3563B1A3BD16DD2CE9FAAAD631357E20945C79E1BCD3C2F3DF89F7D15531FA055669A4433E63910C7BD`

Built, staged and server-installed candidate SHA-256 values match. The production
archive contains the MIT license/notice and no animal QA classes or entrypoint.

Follow-up combined server evidence is recorded below. Still **NOT PROVEN**:
complete visual acceptance including translucent effects, all inventory/assembler
models, physical driving and paver operation, two-player observation, visual
dimension transitions, controller runtime and unrelated addon integration.
Do not publish 26.3 or broaden its compatibility metadata from these server and
unit-test results alone.

## Combined Automobility + Momentum + RIA server

Fresh disposable server: `C:\Users\david\Automobility-26.3-Ecosystem-QA`, bound only
to `127.0.0.1:25663`, with exact production candidates, Fabric API and two separate
QA JARs. RIA's `examples/examplepack-data` was installed before world creation.
The saved successful log is `ecosystem263-pass.log`, SHA-256
`3CA1B50117877822CBA2D95516214FB2FCA14BC622E71263F0449E2FDF3FCF9D`.
The server reached Done, ran all required checks, saved and stopped with exit 0.

This first combined run used pre-text-fix core `B32B664C...E3650A` and the initial
RIA R-default candidate. The exact **final** matrix (core `D0731665...90792DC`,
Momentum `3071A381...53B4C0`, RIA G-default `82E1E204...1F4015`) repeated every
marker below successfully. Retained final log: `ecosystem263-final-gui-pass.log`,
SHA-256 `61F8CF06A6496F355D1C6762082142218B5897695D9F5C6F9C168FFE85E63EE1`.

```text
RIA263_EXAMPLEPACK_PASS
RIA263_PORT_QA_PASS checks=9
AUTO34_ANIMAL_BOARDING_PASS checks=22
AUTO263_PORT_SMOKE_PASS checks=5
AUTO263_ECOSYSTEM_PASS checks=30
```

The additional 30 checks cover actual addon loading, five real Momentum commands,
restored Balanced settings, RIA registry frames, injected four-seat capacity,
animal rejection, seat cycling/cooldown, capacity enforcement and first/last
lorry cargo-slot preservation through vanilla NBT. RIA's nine groups additionally
cover nondefault seat assignments, occupied-seat skipping and the ExamplePack's
loaded behavior. These are actual game objects/mixins, but not physical players.

## Packaged client smoke test

`tools/qa/Start-Client263Qa.ps1` prepares hash-verified isolated runs, and launches
only with explicit `-Launch`. It reads existing caches without modifying normal
profiles. Runtime: Java 25, Fabric Loader 0.19.5, Windows x64 SDL, assets index 34.
The exact mod/runtime manifest is
`C:\Users\david\Automobility-26.3-Client-QA\runs\ecosystem-001\launch-manifest.json`.

The real client launched all three production JARs, connected to the loopback
showcase server on port 25664 and rendered the standard car, RIA quad, lorry,
DMC12 and formula, plus the mechanic-table/assembler scene. The test client has
no QA fixture JAR. Mod Menu 21.0.0-beta.1 and YACL 3.9.6+26.3-fabric were installed
as optional configuration dependencies. Server `momentum sync` reported one
compatible connected client. The initial F2 screenshot is
`runs/ecosystem-001/screenshots/2026-09-19_19.47.32.png` under the client QA root.
This proves startup, connection and a bounded rendering smoke test, not all
interactions, physical controls, every model, or multiplayer driving quality.

Mod Menu opened the real YACL configuration screen successfully. General, Drift
and Camera tabs rendered; server-owned physics fields were locked, while personal
camera settings remained available. General showed `Brake: S` and `Handbrake
(Drift): Space`, verifying the legacy-to-SDL display-name translation. Screenshot:
`runs/ecosystem-001/screenshots/2026-09-19_19.51.17.png`. No settings were changed.
The client disconnected and closed normally at 19:51:31; its retained log is
`runs/ecosystem-001/client001-smoke.log`. This initial client run used RIA's first
26.3 candidate with the old R default; the user subsequently approved a new G
default to avoid Momentum's cruise-resume R collision. Existing saved remaps
must remain untouched, so a fresh candidate/run is required for that final check.

The opt-in `Showcase263Qa` fixture creates only disposable stage/world content.
Early scene attempts exposed changed vanilla command syntax/idempotency and the
JOIN hook running before name-based player selection is ready. Failure logs are
retained in the showcase directory. These setup issues did not modify production
code or candidate hashes. Offline-token Realms errors and the host's OSHI/Perflib
diagnostics are tracked separately from mod/mixin failures.

## Final graphical retest: ecosystem-003-gui

The exact final core `D0731665...90792DC`, unchanged Momentum `3071A381...53B4C0`
and RIA G-default `82E1E204...1F4015` were installed together in a fresh client run.
The client connected successfully; deferred showcase player setup now logged
`AUTO263_SHOWCASE_PLAYER_READY` without the earlier setup failure.
The mechanic table showed its formerly missing **Frames** category title. A QA
assembler populated through its vanilla block NBT displayed its car preview and
Acceleration, Comfortable Speed, Handling and Grip labels. Temporary Night Vision
made the label screenshot readable and was explicitly removed before shutdown.
This is a render/input smoke test, not proof of a complete assembly/crafting flow.

- Client root: `C:\Users\david\Automobility-26.3-Client-QA\runs\ecosystem-003-gui`.
- Category screenshot: `screenshots/2026-09-19_20.06.11.png`.
- Assembler screenshot: `screenshots/2026-09-19_20.07.01.png`.
- Retained client log: `client003-gui-smoke.log`, SHA-256
  `FDAA9D303C67A6502E5CBD81E25CFC6F5FA5B664ECC3EE6D85B3D32BC338CD25`.
- Retained server log: `C:\Users\david\Automobility-26.3-Showcase-QA\showcase-client003-gui.log`,
  SHA-256 `43F7BC413F22BD6423138A9F6938D2F3B3C9F08DAD75ED0E12094693C138ED00`.

Client and server shut down normally at 20:07:37-38; QA ports 25663 and 25664 are
closed. Prior failure logs and earlier candidate client copies remain available.
No public upload, normal-profile installation or production deployment occurred.

## Manual driving feedback: turning stutter (2026-09-19)

The later physical-user run of `ecosystem-003-gui` reported strong stuttering
while driving/turning. The user clarified that the vehicle/camera jerks rather
than the entire rendered image. This is a failed driving acceptance result;
the earlier startup/menu smoke passes must not be promoted to a driving pass.
Publication is blocked until the corrected candidate is retested.

Read-only diagnosis of the actual 26.2/26.3 Minecraft bytecode found a changed
local-player camera interpolation contract. In 26.3 `LocalPlayer.getViewYRot`
uses `getYRot(partial)`, interpolating `yRotO` to current yaw. Automobility's
vehicle tick rotates its driver before `ClientLevel.tickPassenger` calls the
passenger's `commonTick`/`setOldPosAndRot`; the latter overwrites `yRotO` with
the already-rotated yaw. The vehicle-driven turn delta is therefore lost to
camera interpolation. The 26.2 riding path used head-yaw interpolation instead.
A correction must preserve vanilla immediate mouse look and existing physics.

Runtime diagnostics during this session:

- Server `tick query` at 20:22:38 CEST: mean 3.0 ms, P95 4.4 ms, P99 7.0 ms,
  target 50 ms / 20 TPS, 100 samples. This snapshot did not show tick overload.
- 45-second Java Flight Recorder captures under
  `C:\Users\david\Automobility-26.3-Client-QA\diagnostics\`:
  `turn-jitter-client-20260919.jfr` and `turn-jitter-server-20260919.jfr`.
- Client GC: two pauses, maximum 5.4601 ms, total 8.2834 ms.
  Server GC: four pauses, maximum 7.0664 ms, total 14.6579 ms.
  These samples do not prove perfect frame pacing, but do not show long GC stalls.
- Momentum configuration matched on both sides; its locked-camera branch was
  disabled, and the 26.3 steering implementation did not change physics from
  its baseline. Active RIA port sources contain no camera yaw mixin.

A separate network interpolation regression was also identified: 26.3 removed
the vanilla `lerpTo` entrypoint, while the port still declares the old method
and inherits the no-op interpolation handler. This affects remote vehicles /
passengers and remains a separate release-blocking follow-up until corrected
and tested. Do not attribute the local-driver symptom solely to this issue.

### Narrow local-driver correction and retest candidate

The local driver's automobile-driven rotation is now queued during the car tick
and consumed at `LocalPlayer.rideTick` HEAD, after vanilla snapshots old yaw but
before head animation. The one-shot handoff checks rider identity, vehicle tick,
current seat/vehicle, liveness and removal. Dismount/new-tick paths clear it.
Vanilla mouse/view methods, physics, remote/server rotation and the existing
locked-view calculation remain unchanged. This is not the remote interpolation
fix described above.

- Two new compiled-integration tests first failed on the old code, then passed.
- Fixed core suite: 86 tests, zero failures/errors/skips (75 previous + 11 new).
- Momentum suite: 43 tests, zero failures/errors/skips.
- Production JAR: `dist/steering-fix-candidate-26.3/automobility-0.5.0-unofficial.34+26.3-fabric.jar`.
- Size: 1,398,992 bytes.
- SHA-256: `4859E0137AFC93E7C753F90B94CB426834F4A95FF5E54470AFADE58D35BDCD67`.
- SHA-512: `A879D88743544BCC1F69B8700BC14D3FD5D957CFB08A1789C0454F19F3E4FFFDD972A357278D32A6C7676B13825BD916144FFD2F72E626FA34CA8B9AFBE16974`.
- Production archive scan found the new helper/mixin and no QA fixture classes.
- The final headless rerun passed animal 22, core port 5, ecosystem 30 and RIA 9
  groups plus `RIA263_EXAMPLEPACK_PASS`, then stopped normally. Retained log:
  `C:\Users\david\Automobility-26.3-Ecosystem-QA\ecosystem263-steering-fix-pass.log`.
  An earlier run omitted the ExamplePack opt-in property and is retained under
  `ecosystem263-steering-pass-without-example-gate.log`; it is not full coverage.

Manual retest run: `C:\Users\david\Automobility-26.3-Client-QA\runs\ecosystem-004-steering`.
It keeps the previous test client's options and Momentum configuration, the same
saved showcase world/inventory, unchanged Momentum/RIA production JARs, and the
new core. The user explicitly approved restarting this isolated test game.
Before replacement the prior world, JAR and manual-failure logs were copied to
`C:\Users\david\Automobility-26.3-Showcase-QA\backups\before-steering-fix-20260919`.

This retest alone includes the separate client-only passive telemetry fixture
`automobility-client-driving-qa.jar` (4,727 bytes, SHA-256
`480C6D55EC2D8885CA8B5C72A4E5F16CC24ED5303B7CF86750FA06AB0F9F5D65`).
It records at most 120 turning ticks, never changes inputs/world state, and logs
view yaw at partial ticks 0/0.5/1. Keep it out of public artifacts and normal
profiles. No physical-user pass is inferred from numerical interpolation alone.
Physical acceptance of the fix remains PENDING.

The fixed client connected at 20:34:39 CEST and the showcase was visually checked
after restart. Its log confirms `AUTO263_DRIVER_QA_READY` with the expected three
mod versions. At handoff the passive observer had no turning samples yet, so no
numeric or physical turning pass is claimed. The world and client remain running
for the user's retest. Final server-regression log SHA-256:
`5DF161E15856AE1D30A413A4D0B827C06629D34BBEA03029E45AE63EEB9F5231`.

### Physical turning acceptance received

The user subsequently confirmed "ano už ok" for the steering/camera retest with
core `4859E013...BDCD67`. Passive telemetry at 20:37:16 CEST completed 120 turning
samples: 120 interpolated, zero flat-view and zero nonlinear samples. This agrees
with the user's comfort report; it is acceptance of the reported turning issue,
not an assumed pass for all cargo/crafting, controllers or two-player scenarios.
Retained client log: `runs/ecosystem-004-steering/client004-steering-user-pass.log`,
SHA-256 `CF73E20356699C6436889E3FF6E514A3E7A7C8C25CE03D2EF3D691BF0828FEA7`.
The separate remote-interpolation correction was completed and technically
verified below. Broader manual release gates remain open.

### Remote interpolation correction and approved restart

Minecraft 26.3 now enters Automobility's client `InterpolationHandler`, rather
than the obsolete vanilla `lerpTo` callback. The handler retains the queued
endpoint for position-only/rotation-only packets and shortest-path yaw, and
advances exactly once in the existing automobile position-tracking phase after
`prevPos` capture. Vanilla `commonTick` does not advance it a second time.
Remote visual steps leave the network position codec unchanged.

Authoritative packets are bracketed without prematurely deleting the queued
endpoint used to resolve relative teleports. Hard corrections, same-position
teleports, native far-distance/non-ticking snaps, and driver takeover discard
stale interpolation. Local-driver rotation and physics remain unchanged from
the accepted steering candidate. Hitboxes retain immediate/no-op interpolation.

- Initial two compiled-entrypoint regression tests failed before the correction.
- Final core suite: 101 tests; Momentum suite: 43 tests; zero failures/errors/skips.
- Production JAR: `dist/interpolation-fix-candidate-26.3/automobility-0.5.0-unofficial.34+26.3-fabric.jar`.
- Size: 1,401,851 bytes.
- SHA-256: `9DF5ACCF8E744D4904BFEE6CFD953AE6ADB3F80032AFCCECB3E02B798A4BBE1C`.
- SHA-512: `C020FE5B6E448F48A7E4FEA466058DE1D366F7156BD2AB6C0BF41D9F5396E17095275F88E61C2BF956D3C34ABA7C49EDB0A1B22BA4EA01F92F4652403E4ECF37`.
- Production archive: 763 entries, no QA fixture classes. Client and both isolated
  QA server copies match the candidate SHA-256.
- Packaged headless server rerun at 20:52:30 CEST passed animal 22, core port 5,
  ecosystem 30, RIA 9, and `RIA263_EXAMPLEPACK_PASS`, then stopped normally.
  Log: `C:\Users\david\Automobility-26.3-Ecosystem-QA\ecosystem263-interpolation-fix-pass.log`.
  SHA-256: `5FB1B0B563EC14DB0E9ACA8C6ECBFBF1BFE66B5C05FA8FCC534922BFDA59EEF7`.

The user explicitly approved completing the technical check and restarting only
the test Minecraft. The previous client disconnected, the server saved and
stopped normally, and the world/logs/old core were copied to
`C:\Users\david\Automobility-26.3-Showcase-QA\backups\before-remote-fix-20260919`.
The existing world, cars and player inventory were retained; the new client run
`C:\Users\david\Automobility-26.3-Client-QA\runs\ecosystem-005-interpolation`
keeps run 004's options/configuration and unchanged Momentum/RIA production JARs.

The separate test-only client fixture is 12,744 bytes, SHA-256
`7FAF7669383703EBF87875EE1082E93DD843BB667D21B4BA165C438EB5721510`.
Only this child launch opted in with `AUTOMOBILITY_QA_REMOTE_INTERPOLATION=1`.
It uses a temporary, client-only automobile and the real packet handlers on the
loopback server as Auto263QA; no real player is mounted or controlled, and the
temporary entity is removed within the same callback. It does not send movement
packets to the server or mutate existing vehicles, inventory or blocks.

At 20:54:02 CEST, the packaged client reported
`AUTO263_REMOTE_INTERPOLATION_PASS checks=46 realPacketHandlers=true physicalTwoPlayerAcceptance=NOT_PROVEN`.
Coverage includes tick ordering, mixed position/rotation updates, codec
preservation, yaw wrapping, +17Y hard / +6Y relative movement, -23Y teleport,
same-position and relative teleports, eager authority takeover and return,
far-distance and unloaded/non-ticking snaps, and fixture cleanup.
Retained log: `runs/ecosystem-005-interpolation/client005-remote-interpolation-pass.log`,
SHA-256 `44C3AC7C4816900A2D6BC76C82804C0DEDEF66CBAF81AECB2FE2CBD76D054FD0`.

Computer Use confirmed the correct 26.3 multiplayer window after restart and
returned it from the pause menu to the preserved showcase. The client and
loopback showcase server remain running for the user's test. The other 26.2
Minecraft window was not touched. No public upload, source push, normal-profile
installation or production deployment occurred.

Remaining coverage is explicitly NOT PROVEN: a complete physical-user pass of
cruise/drift, cargo/crafting and save/rejoin on this final candidate; two real
players driving/riding/changing seats and dimensions; optional controller runtime.
The user's earlier turning acceptance remains valid evidence for that correction,
not a claim that these other scenarios were performed.

### Final requested manual checks accepted (2026-09-19)

After the final `ecosystem-005-interpolation` restart and technical pass, the user
was explicitly asked to try cruise control, G seat switching and cargo. The user
answered "ano vše ok". This accepts those requested checks with core 9DF5ACCF,
Momentum 3071A381 and RIA 82E1E204. It complements the earlier physical turning
acceptance and authorizes continuing the already requested Modrinth beta release.
No two-real-player test, full crafting/save-rejoin/destruction matrix, custom-key
migration trial or controller test is inferred; those remain NOT PROVEN.
The final staged core is copied byte-for-byte to the canonical release-record
path, with the old GUI candidate preserved under
`dist/previous-candidates/gui-before-final-release-20260919`.
