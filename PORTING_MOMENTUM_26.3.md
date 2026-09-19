# Momentum Minecraft 26.3 release

Status: **published beta q2J8dtUO; anonymous artifact verified**. The module build, input-contract tests and
combined Automobility + Momentum + RIA dedicated-server regression checks pass.
An isolated graphical client connected and passed Momentum options-screen smoke checks.
The user accepted turning and the requested cruise-control check in the final
three-mod client. Full end-to-end two-real-player/controller coverage is unproven.
The fresh public CDN download matches the same tested JAR below. See
`MOMENTUM_MODRINTH_RELEASE_26.3.md` for the version/dependency/source receipt.

## Changes

- Exact Minecraft 26.3 / Java 25 / Fabric Loader 0.19.5 / Fabric API 0.161.0+26.3 metadata.
- Automobility minimum raised to `0.5.0-unofficial.34` (use its matching 26.3 build).
- Source and issue URLs now point to `git.offchunk.com/Offchunk/automobility-unofficial-port`.
- Registered controls use Minecraft's SDL `KEYBOARD` scancodes. Raw brake/drift polling uses
  `InputConstants.isKeyDown`, with no removed GLFW library/window-handle calls.
- Existing JSON key integers are retained and converted only for input/display, including
  unbound, letters, digits, punctuation, modifiers, navigation, keypad, and F1-F24. This avoids
  interpreting legacy S (`83`) as SDL Num Lock. Platform-specific WORLD_1/WORLD_2 and F25
  fail closed as unbound because there is no unambiguous portable mapping; see `momentum/README.md`.
- Server-side config defaults/validation no longer reference GLFW. Physics, camera, HUD,
  gameplay synchronization, localization and the optional Controlify adapter are retained.

## Public optional dependencies checked 2026-09-19

- [YACL 3.9.6+26.3-fabric](https://modrinth.com/mod/yacl/version/rSnhz5tr).
- [Mod Menu 21.0.0-beta.1](https://modrinth.com/mod/modmenu/version/lcxkZSq6).
- [Controlify Fabric 26.3 query](https://api.modrinth.com/v2/project/controlify/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D)
  returned no versions. The real optional adapter is compiled against the existing
  `3.0.2+26.2-fabric` API, not bundled or replaced by stubs. Runtime Controlify compatibility
  on 26.3 is **NOT PROVEN**; do not override the 26.2 mod's Minecraft requirement.

## Build evidence

JDK: Temurin 25.0.3+9. Command from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Users\david\.jdks\temurin-25-20260619175454\jdk-25.0.3+9'
.\gradlew.bat :momentum:test :momentum:build --max-workers=2 --console=plain
```

Result: **43 tests, 0 failures/errors, 0 skipped** (34 existing + 9 key/config regressions).
Test XML and HTML are in `momentum/build/test-results/test` and
`momentum/build/reports/tests/test`.

Production JAR: `momentum/build/libs/momentum-0.1.11-unofficial.10+26.3-fabric.jar`.
Size: `216016` bytes.

```text
SHA256 3071A38108E49CB2BAF8B6A56AC0B6E4DFB0F332AA2929A3EB10ABEFB053B4C0
SHA512 9E6322F78A9129EFE77F95F0B5B5D857DFEABCD97E538B8C41D65581467D9A5D05FAE9E9ACA0C6448CCB9BF33C5C94A0DAC3AE293039D6C7F1D8BF741CC3706C
```

The built manifest has expanded versions, exact Minecraft 26.3, updated Offchunk URLs,
MIT license and optional (not required) UI/controller dependencies. The JAR includes its
license and real Controlify adapter; test/QA classes are not packaged.

## Combined dedicated-server evidence

The exact Momentum artifact above was loaded with Automobility `.34+26.3` and
RIAutomobility `.6+26.3` in the isolated `Automobility-26.3-Ecosystem-QA` server.
The actual server completed these opt-in fixtures and stopped cleanly:

```text
RIA263_EXAMPLEPACK_PASS
RIA263_PORT_QA_PASS checks=9
AUTO34_ANIMAL_BOARDING_PASS checks=22
AUTO263_PORT_SMOKE_PASS checks=5
AUTO263_ECOSYSTEM_PASS checks=30
```

The 30 ecosystem checks include loading the real addon mixins, invoking Momentum's
`status`, `preset sport`, `reload`, `sync` and `preset balanced` commands through the
server command dispatcher, and confirming the restored Balanced server preset.
They also cover RIA frame registration, four-seat capacity/switching/cooldown, rejection
of animal passengers, and the lorry's 54-slot cargo persistence with Momentum present.
The example-pack marker and nine RIA checks independently exercise the 26.3 data contracts.

Log: `C:\Users\david\Automobility-26.3-Ecosystem-QA\ecosystem263-pass.log`.
SHA256: `3CA1B50117877822CBA2D95516214FB2FCA14BC622E71263F0449E2FDF3FCF9D`.

This is real dedicated-server execution, not a graphical client or human driving test.
The `sync` command completed without connected clients; client receipt, application and
reconnect behavior are **NOT PROVEN** by this run. Client-only mixins were not exercised.

## Graphical client smoke: ecosystem-001

The real packaged-JAR Minecraft 26.3 client connected to the isolated loopback showcase
server with Momentum, Automobility, RIA, YACL and Mod Menu. The Momentum options screen
opened successfully; General, Drift and Camera tabs were inspected. Server-controlled
gameplay options were disabled while personal camera options remained available.
The General page correctly displayed **Brake: S** and **Handbrake (Drift): Space**, verifying
the legacy-to-SDL display mapping in the actual client (not just a unit test).

At `19:47:18` the server reported `Synced config to 1 compatible client(s)`; the observed
server-controlled UI confirms the client had applied the authoritative-config state.
This does not prove every gameplay field's value or reconnect behavior. The client and
server shut down cleanly at `19:51:31`.

Evidence, 2026-09-19:

- Screenshot: `C:\Users\david\Automobility-26.3-Client-QA\runs\ecosystem-001\screenshots\2026-09-19_19.51.17.png`.
  SHA256 `AA1960DDD075113B1CBC0D895E2C9B3C7C7AF6C0FC0C8575368C5F8EACDA5791`.
- Client log: `C:\Users\david\Automobility-26.3-Client-QA\runs\ecosystem-001\client001-smoke.log`.
  SHA256 `CCCE6C9C90D6A74427D38627ED26D401451F4B25CEB19D0A92A441B743A6100E`.
- Server log: `C:\Users\david\Automobility-26.3-Showcase-QA\showcase-client001-pass-with-setup-warning.log`.
  SHA256 `E64254D74E415EC004B6991BDD89579D9E4417FEFB90D37A6E3AE57FFF90736F`.

The server log retains a QA-fixture player-setup timing warning; the viewing position was
corrected through the isolated server console. Deferred player setup was subsequently fixed
in the test fixture and requires a follow-up run; this warning was not silently omitted.
The offline client's authentication diagnostics and host performance-counter warning are
also retained. This record does not claim a warning-free log or physical driving acceptance.

### RIA seat-switch binding collision

The initial stack had RIA seat switching and Momentum cruise resume both bound to `R`.
The user selected **G** for the RIA seat-switch default; Momentum resume remains **R**.
The updated RIA build and new client run are tracked separately and are not proven by
the `ecosystem-001` screenshot. Previously saved player bindings must be checked explicitly;
changing a default should not silently overwrite a player's customized controls.

## Remaining acceptance gates

The later user confirmation "ano vše ok" accepted the requested cruise-control,
G seat-switch and cargo checks on `ecosystem-005-interpolation` with this unchanged
Momentum JAR and final core 9DF5ACCF. It is not an exhaustive sub-action or
two-player/controller test. See the final acceptance record in `PORTING_26.3.md`.

- Follow-up client smoke passed: `ecosystem-002-g` used RIA G / Momentum R and
  actual G input changed server-side seat assignments. `ecosystem-003-gui` then
  connected with the final core GUI fix and corrected showcase setup. Momentum's
  exact JAR remained unchanged. See `PORTING_26.3.md` for final logs and hashes.
- Validate individual authoritative gameplay values and reconnect handling with connected clients.
- Complete vehicle/dashboard rendering and remaining options-screen interactions; the smoke above
  proves screen loading and inspected labels/tabs, not every setting or gameplay effect.
- Physical driving: brake/drift, cruise toggle/adjust/resume/cancel, camera, sounds and reconnect.
- Two real clients / observer synchronization and physical controller testing once Controlify
  ships a compatible build. Unit tests do not establish those player-facing outcomes.
- Recheck dependencies and exact final hashes before publication; no ordinary profile or
  production-server install is part of this module work.
