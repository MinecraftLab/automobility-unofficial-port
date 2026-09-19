# Minecraft 26.3 core port - local candidate

Status on 2026-09-19: **UNPUBLISHED / CLIENT ACCEPTANCE NOT PROVEN**.
Branch `port/26.3`, initially based on Automobility `.34` commit `36f0044`.
No Minecraft profile or production server was changed. This is a core Automobility
port, not a coordinated addon release.

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

Do not report `tools/qa/Test-Automobility.ps1` or an unqualified whole-workspace
`build` as passed for 26.3. That existing combined release gate requires target
release records and Momentum artifacts; neither has been ported or approved here.
The only Momentum change in this branch is the shared Loom plugin version, needed
to configure the multi-project build consistently. No Momentum source/API change,
build, runtime validation, artifact or release is claimed.

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

- Bytes: `1396426`
- SHA-256: `B32B664C83605E69B1DE76B8BE621EB520FF686337944D45519D7C1815E3650A`
- SHA-512: `369347D7EFE8A933333CACC6D3575FF3800CFB7EE7635CEB9C6898DCA202D076AEAD2D1BE482ADA30A52E6F9E7755E773BFCD68D2B0535FF39D5509B6DBC5A20`

Built, staged and server-installed candidate SHA-256 values match. The production
archive contains the MIT license/notice and no animal QA classes or entrypoint.

Still **NOT PROVEN**: actual client startup/mixin application, rendering including
translucent effects, inventory/assembler models, mouse/keyboard/FOV behavior,
manual driving and paver operation, two-player observation, visual teleport and
dimension transitions, `/reload` and addon integration. Static checks of the
client mixin target signatures do not replace launching and playing the client.
Do not publish 26.3 or broaden its compatibility metadata from these server and
unit-test results alone.
