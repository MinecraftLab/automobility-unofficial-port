# Minecraft 26.3 core port - local candidate

Status on 2026-09-19: **UNPUBLISHED / CLIENT SMOKE PASSED / MANUAL ACCEPTANCE PENDING**.
Branch `port/26.3`, initially based on Automobility `.34` commit `36f0044`.
No normal Minecraft profile or production server was changed. The initial core
port has now been extended to Momentum and tested with a separate RIAutomobility
26.3 worktree. All three remain unpublished candidates, not an accepted release.

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
