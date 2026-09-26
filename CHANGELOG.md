# Changelog

## Automobility 0.5.0-unofficial.35 / Momentum 0.1.11-unofficial.10 - 2026-09-26 (BETA)

Target-specific Fabric builds for Minecraft Java 26.1.2 and 26.2. Momentum `.10`
requires the matching Automobility `.35` core; this entry does not change other
Minecraft release lines or their requirements.

### Fixed and changed

- Split the optional core and Momentum controller integrations into separate
  adapters for the tested Controlify `3.0.2` and `3.5.3` SDK/API families. Existing
  binding IDs are preserved; compatibility with arbitrary future SDKs is not promised.
- Replaced removed modern guide/radial APIs with contextual-guide resources and
  four Momentum radial-menu items: set/cancel cruise, increase, decrease, and resume.
- Kept Controlify entirely optional and neither SDK bundled. Unknown API families
  disable the optional integration while retaining keyboard controls; physical
  controller buttons remain unassigned by default.
- Retained the legacy Controlify integration through its separately compiled adapter.

### Verification status

- Both targets passed the final clean no-cache build/artifact gate and **119 tests
  each** (82 core, 37 Momentum), with 0 failures and 0 skipped tests. Regression
  coverage includes both real SDK families, controller-call linkage, and guide/radial
  resources; QA fixtures are excluded from production JARs.
- Target-specific runtime matrices are recorded in the [26.1.2 release record](MOMENTUM_MODRINTH_RELEASE_26.1.2.md)
  and [26.2 release record](MOMENTUM_MODRINTH_RELEASE_26.2.md), not inferred
  from successful builds. Fresh physical-controller gameplay, two-real-player
  driving, and full-modpack acceptance remain **NOT PROVEN** for these betas.
- Earlier gameplay/modpack reports are historical evidence, not acceptance of
  this release. Publication and public-download identity are tracked by each
  target-specific release record.

## 0.5.0-unofficial.34 - 2026-09-19 (BETA)

### Fixed

- Animals no longer board automobiles automatically or through normal mounting,
  including rear passenger seats. This covers land animals, aquatic animals and bats.
- Animals already riding an automobile in an older save safely dismount on the
  next server tick. Players and non-animal mobs retain their existing boarding rules.
- Carries forward the `.31` security/data-safety and `.33` position-sync rendering repairs.
- Source and issue links now use git.offchunk.com.

### Verification status

- Both targets passed 107 unit tests and the exact artifact gate. A separate
  dedicated-server fixture passed 22 animal-boarding scenario groups per target,
  including 17 animal types, rear seats and mixed-passenger NBT reconstruction.
- Final Offchunk artifacts were rebuilt and server-tested on 2026-09-19. Exact
  publication/download evidence is in the target-specific release record.
- Previous `.33` evidence remains historical; fresh manual driving, two-client
  observation and full modpack acceptance remain unproven for `.34`.

## 0.5.0-unofficial.33 - 2026-09-08 (UNPUBLISHED CANDIDATE)

Client position-packet reconciliation for the unofficial Minecraft Java 26.1.x
and 26.2 Fabric ports. This candidate supersedes the unpublished, runtime-rejected
`.32` candidate and carries forward the `.31` security and data-safety changes.

### Fixed
- Reconciled automobile render displacement after the absolute position-sync packet
  used by the server for large observer relocations, while retaining the vanilla
  teleport-packet path used for riding players.
- Rebased client-local suspension displacement after smaller relative position
  updates without clearing Automobility's normal movement interpolation.
- Refreshed destination terrain after a position change and closed the ordering seam
  where legacy component synchronization could move an automobile before the vanilla
  position packet was applied.
- Aligned server displacement before serializing legacy component state so a position
  change cannot expose the previous vertical sample in the same tick.

### Verification status
- The Java 25 preparation gate passed 107 tests (Fabric 73; Momentum 34), with
  0 failures and 0 skips. Target-specific partial runtime results are recorded in
  the target release records; manual-driving, two-client observer and publication
  gates remain pending.

## 0.5.0-unofficial.32 - 2026-09-08 (unpublished; runtime rejected)

Attempted client-rendering repair for the unofficial Minecraft Java 26.1.x and
26.2 Fabric ports. This candidate must not be published or integrated.

### Rejected
- The patch intercepted only the teleport-entity packet. A live 26.2 regression test
  proved that a large teleport of an empty tracked automobile instead arrives through
  the absolute entity-position-sync path: its entity and hitbox moved, but its body
  model remained at the previous altitude until returning or reconnecting.
- Both `.32` JARs and their source bundles are retained as immutable failure evidence;
  their exact identities are recorded in `FAILED_RELEASE_CANDIDATES.md`.

## 0.5.0-unofficial.31 - 2026-09-04

Security, data-safety and release-verification update for the unofficial
Minecraft Java 26.1.x and 26.2 Fabric ports.

### Security
- Stopped accepting client-authored physics, position and collision-damage values
  from Automobility's legacy vehicle-state packet. The wire layout remains compatible,
  while the server now consumes only bounded, fresh driving input and computes gameplay speed.
- Required real server-observed vehicle movement for run-over damage and capped impact
  speed to that movement, preventing stale input from damaging entities around a stationary car.
- Restricted attachment-component detail requests to valid payloads, tracked nearby
  automobiles and a bounded per-player request budget.
- Applied world-border and vanilla spawn-protection checks to harvester and plow block
  changes, including autopilot and mob-controlled vehicles, while retaining
  Automobility's explicit allow-block behavior outside protected areas.

### Fixed
- Removed the unconditional per-tick forced velocity update for every automobile.
- Made raw automobile items without custom component data place a playable default car.
- Invalidated attachment menus when their attachment is removed or replaced, the car is
  gone, the player changes dimension or leaves range. Chest and banner inventories close
  safely and drop or transfer their original stacks only on the server.
- Bounded data-pack frame weights, engine torque and speed, wheel size and grip,
  wheel radius, and per-wheel display scale to finite ranges that keep vehicle physics safe.
  Accepted ranges are respectively `0.01-2.5`, `0-3`, `0-4`, `0-4`, `0-1`,
  `0.25-64` pixels and `0.01-2`.
- Preserved supported RIAutomobility `.6` component sync and kept extreme data-pack
  combinations from producing reversed acceleration, reversed steering or velocity gain.
- Preserved unresolved frame, wheel and engine registry IDs across saves so temporarily
  missing add-on data is not silently replaced by Automobility's empty fallback.
- Preserved opaque front/rear attachment NBT, including nested inventory data, while its
  add-on registry type is temporarily unavailable; an explicit replacement still clears it.
- Expanded dismount searches and collision-checks before using the above-car fallback.

### Changed
- Added one-command local/CI QA for clean tests, final JAR metadata and hash verification.
- Bundled project notices and the MIT license notice for the embedded Obj 0.4.0 library.

## Momentum 0.1.11-unofficial.8 - 2026-08-31

Driving-tune and multiplayer-options release for the unofficial Minecraft Java
26.1.x and 26.2 Fabric ports of Momentum for Automobility.

### Added
- Added Touring, Balanced and Sport driving tunes, plus a Custom tune for manual
  movement and steering settings.
- Preserved the player's hand-tuned chassis values while a named tune is active and
  restored them when Custom is selected again.

### Changed
- Extended coasting across every named tune while retaining progressively firmer
  behavior from Touring through Sport.
- Kept drift, camera, HUD, sound, key bindings and cruise-control preferences
  independent from driving-tune changes.
- Made server-controlled gameplay settings display the server's real values as
  read-only in multiplayer while camera and HUD options remain personal.
- Added a one-time migration that adopts Balanced without overwriting later manual
  choices or unrelated settings.

## 0.5.0-unofficial.30 - 2026-08-28

### Fixed
- Kept rear attachments aligned with their automobile after large same-dimension
  teleports while preserving normal towing physics, reverse articulation, vertical
  movement and even extreme in-tick Momentum speeds.
- Restored localized inventory names for Automobility's six block items instead of
  exposing raw `item.automobility.*` translation keys.
- Removed stale mixin refmap declarations that referenced a file no longer emitted
  by the current Fabric Loom build.

## Momentum 0.1.11-unofficial.7 - 2026-08-25

Dual-version dashboard and localization release for the unofficial Minecraft Java
26.1.x and 26.2 Fabric ports of Momentum for Automobility.

### Added
- Localized Momentum's complete options screen, status notifications and debug HUD
  in the same 17 languages shipped by the Automobility port.
- Added automated locale validation for the exact language set, complete key parity,
  non-empty values and preserved formatting placeholders.

### Changed

- Moved the complete compact driving cluster to the bottom-left by default while
  preserving the configurable right-edge anchor for users who choose it.
- Added a persisted one-time layout migration for the two exact Rosenvik presets,
  without moving custom coordinates or overriding later user changes.
- Kept the Fuel System reflection contract and narrow-screen vertical clearance intact.

## Momentum 0.1.11-unofficial.6 - 2026-08-24

- Rebuilt the compact HUD as a stable 104 x 17 bottom-right speed instrument with
  ten speed/boost segments that never reflow when cruise control changes state.
- Moved the cruise target into its own aligned 38 x 11 top pod while retaining the
  exact target marker on the main speed bar.
- Added responsive narrow-screen clearance and a selective migration for the exact
  previous Rosenvik compact preset without moving explicitly positioned user HUDs.
- Kept the legacy dashboard mode and public Fuel System reflection contract intact.

## Momentum 0.1.11-unofficial.5 - 2026-08-24

HUD polish update for the unofficial Minecraft Java 26.2 Fabric port of Momentum for Automobility.

### Added
- Added a compact unified driving dashboard with a speedometer icon, exact speed,
  graphical km/h unit, visible empty segments, boost coloring and cruise target marker.
- Added a YACL toggle that keeps the previous configurable bar-and-text HUD available
  as a legacy layout.

### Changed
- Kept every compact HUD element inside Momentum's declared 90 x 15 pixel panel so
  companion overlays can anchor without guessing at hidden text bounds.
- Matched the panel chrome and segment treatment used by Automobility Fuel System 1.5.

## 0.5.0-unofficial.29 - 2026-08-24

### Fixed
- Made automobile hitboxes handle Minecraft 26.2's nullable hard-collision query,
  preventing a dedicated-server crash during natural spawn collision checks with Lithium.

## 0.5.0-unofficial.28 - 2026-08-14

Compatibility and translation update for the unofficial Minecraft Java 26.1.x and 26.2 Fabric ports.

### Added
- Added complete Czech, Polish, and Korean translations using current Automobility keys and official Minecraft terminology for vanilla content.
- Added component-specific Auto Mechanic Table input ingredients so addons can require an exact frame, wheel, engine, or attachment variant.

### Fixed
- Completed the Simplified Chinese translation and replaced obsolete MidnightControls keys with the current Controlify bindings.
- Made translation validation require every locale to match the complete English key set while preserving formatting placeholders.
- Preserved component-specific recipe requirements in the mechanic table UI, shift-click matching, and client-server recipe sync.

## 0.5.0-unofficial.27 - 2026-08-13

Translation update for the unofficial Minecraft Java 26.1.x and 26.2 Fabric ports.

### Added
- Added German, Spanish, French, Hindi, Italian, Japanese, Brazilian Portuguese, Russian, Urdu (Pakistan), Vietnamese, and Traditional Chinese translations contributed through pull request #8.
- Added a complete Ukrainian translation using current Automobility keys and official Minecraft terminology for vanilla content.
- Added automated validation for translation JSON, known keys, and formatting placeholders.

### Fixed
- Restored numeric placeholders and the speed unit in the Hindi translation.
- Corrected wording in the German, Vietnamese, and Traditional Chinese translations.

## 0.5.0-unofficial.26 - 2026-07-11

Patch release for the unofficial Minecraft Java 26.1.x and 26.2 Fabric ports.

### Fixed
- Preserved all autopilot heading vectors when routes are saved and loaded.
- Counted multiple copies in the same inventory stack correctly for Auto Mechanic Table recipes with repeated ingredients.
- Fixed the sticky-slope grace timer and block sampling at negative coordinates.
- Prevented a false first-tick vehicle collision measurement after an automobile is spawned or loaded.
- Guarded autopilot obstacle checks against stale hitboxes and clamped attachment animation states to valid values.
- Prevented client-side automobile creation from calling Fabric's server-only entity tracking API, which disconnected worlds containing existing vehicles with a network protocol error.

### Changed
- Limited periodic client state uploads to the locally driven automobile.
- Validated automobile state packet sizes and numeric values, capped packet size, and rate-limited accepted client syncs.
- Distributed automobile state through actual entity tracking and reduced visual state updates to the existing four-tick interpolation interval.
- Added automated regression tests for autopilot serialization, repeated recipe ingredients, and network state validation.

## Momentum 0.1.11-unofficial.4 - 2026-07-11

Patch release for the unofficial Minecraft Java 26.1.x and 26.2 Fabric ports of Momentum for Automobility.

### Added
- Added working optional Mod Menu integration when YACL is installed.
- Added backward-compatible server-to-client gameplay configuration sync so vehicle physics use the server's settings in multiplayer.
- Added remote brake and drift input sync for automobiles tracked by other clients.
- Added Controlify brake and drift input through Automobility's existing controller bindings.

### Fixed
- Restricted client input to the automobile driven by the local player instead of applying static brake or drift state to every loaded automobile.
- Ignored raw brake and drift keys while a chat, menu, or other screen is open.
- Made automatic drift direction deterministic across the client and server and used automobile-specific ground detection on slopes.
- Recovered safely from malformed configuration files, validated unsafe numeric values, and wrote configuration updates atomically.
- Reset key-state synchronization between multiplayer connections and stopped rendering a filled speed bar at zero speed.

### Changed
- Moved the default speed HUD position to the upper-right corner for new configurations.
- Kept HUD, camera, and key preferences client-side while treating movement, steering, drift, and cruise behavior as server-authoritative in multiplayer.

## 0.5.0-unofficial.25 - 2026-06-30

Patch release for the unofficial Minecraft Java 26.x Fabric port.

### Fixed
- Made client-side auxiliary automobile hitboxes inert while their parent automobile entity is missing. This prevents stale invisible hitboxes from mounting players into an automobile that the client is not tracking.

## 0.5.0-unofficial.24 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Made auxiliary automobile hitbox entities honor authoritative server position packets on clients instead of continuously deriving collision positions from the parent automobile. Hitboxes now request precise position tracking and update every tick, while stopped automobile sync still performs an immediate local snap for parked vehicles.

## 0.5.0-unofficial.23 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Included the automobile's authoritative server position in the custom sync packet and snapped stopped client-side automobiles to that position before recalculating auxiliary hitboxes. This prevents parked vehicles that coasted after a dismount from keeping a stale rear hitbox until the next small physical movement.

## 0.5.0-unofficial.22 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Kept automobile hitbox collision active for recently dismounted players, but now continuously resolves overlap while the vehicle is settling after an unmanned coast. This prevents players from entering the vehicle hitbox during the grace window and getting stuck when collision resumes.

## 0.5.0-unofficial.21 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Kept the recent-dismount collision grace active for the full unmanned coasting window plus a short settle period, preventing the former driver from getting stuck on the automobile hitbox after jumping out while the vehicle rolls to a stop.

## 0.5.0-unofficial.20 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Stopped recreating auxiliary hitbox entities every time an unmanned automobile finishes coasting; the settle path now validates and repositions the existing hitboxes like a normally stopped vehicle.

## 0.5.0-unofficial.19 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Moved recently dismounted players out of current auxiliary automobile hitboxes after dismount stabilization and after unmanned coasting settles, reducing edge jitter when the vehicle stops near the player.

## 0.5.0-unofficial.18 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Pruned stale duplicate auxiliary hitboxes on the client after server-side hitbox recreation so local player collision uses the current hitbox set.

## 0.5.0-unofficial.17 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Removed direct living-entity collision from the main automobile entity so players collide with the detailed auxiliary hitboxes instead of two overlapping vehicle collision layers.
- Restored normal auxiliary hitbox collision for recently dismounted players after the previous test build made overlap jitter worse.

## 0.5.0-unofficial.16 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Kept recent-dismount collision grace active while an unmanned automobile coasts to a stop.
- Ignored automobile and auxiliary hitbox collision against the player who just dismounted, reducing camera jitter when walking into the vehicle immediately after a moving dismount.
- Removed orphaned auxiliary hitbox entities on the server if they are no longer tracked by their automobile.

## 0.5.0-unofficial.15 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Made automobile and auxiliary hitbox entity collision checks symmetric.
- Rebuilt auxiliary hitboxes after an unmanned automobile finishes coasting, preventing stale collision areas from lingering on some vehicle parts.

## 0.5.0-unofficial.14 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Changed
- Moved the in-vehicle control hints to the upper-right HUD area by default.

## Momentum 0.1.11-unofficial.3 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port of Momentum for Automobility.

### Fixed
- Prevented the drift key from pushing automobiles into reverse when held at low speed; reverse remains on the brake key.

## 0.5.0-unofficial.13 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Settled unmanned automobiles once they finish coasting after a moving dismount, clearing residual physics and resynchronizing hitboxes.

## 0.5.0-unofficial.12 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Restored physical collision for detailed automobile hitbox entities so rear vehicle sections cannot be walked through while the automobile coasts after dismount.
- Restored Automobility control hints when Momentum's HUD is enabled.

## 0.5.0-unofficial.11 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Changed
- Allowed automobiles to coast after the driver dismounts while still clearing driver input and stabilizing hitbox synchronization.
- Stopped active boost on driver dismount so unmanned automobiles coast from their current engine speed instead of continuing powered boost.

## 0.5.0-unofficial.10 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Prevented moving dismount fallback from placing the player at the center of the automobile.
- Tried both sides of the automobile when choosing a dismount location and increased side clearance.

## 0.5.0-unofficial.9 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Forced vehicle interpolation and hitbox positions to settle immediately when the driver dismounts while moving.
- Restored normal walking collision immediately after dismount while keeping the recent-dismount protection against run-over knockback.
- Gave the motorcar windshield/dashboard planes a tiny depth to avoid zero-thickness face flicker during engine shake.

## 0.5.0-unofficial.8 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Rendered only the transparent upper windshield pixels through the motorcar glass pass.
- Moved the opaque lower windshield/dashboard pixels back into the normal motorcar body pass to reduce flicker while the engine shake animation is active.

## 0.5.0-unofficial.7 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Restored the full motorcar windshield height after the previous visual test build left a gap.
- Moved the separate motorcar windshield plane away from nearby body geometry to reduce flickering under the windshield.

## 0.5.0-unofficial.6 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Stabilized vehicles immediately when the driver dismounts, clearing residual speed and collision velocity left by jumping out while moving.
- Split motorcar glass into its own translucent render pass so the body can render separately from the windshield.

## 0.5.0-unofficial.5 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Restored the motorcar windshield plane position and instead offset the body face behind it to target dashboard-area z-fighting.
- Prevented detailed automobile hitbox entities from acting as direct walking collision for living entities; player collision now uses the stable main automobile entity instead.

## 0.5.0-unofficial.4 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Offset the motorcar windshield plane slightly away from the front body face to reduce dashboard-area z-fighting.
- Kept automobile hitbox entities on stable chassis coordinates instead of visual suspension displacement to reduce player collision jitter after driving.
- Added a short dismount grace period so the former driver is not immediately pushed or run over by the same vehicle.

## 0.5.0-unofficial.3 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Changed
- Changed motorcar frame rendering away from full-model translucent rendering to reduce windshield shimmer.
- Made automobile hitbox collision active consistently on client and server to reduce player clipping and jitter after driving.

## Momentum 0.1.11-unofficial.1 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port of Momentum for Automobility.

### Changed
- Reduced the cruise control HUD chevron size.
- Made the cruise control HUD color stable while active instead of switching with throttle/coast state.

## Momentum 0.1.10-unofficial.1 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port of Momentum for Automobility.

### Changed
- Replaced the cruise control HUD icon with compact procedural chevrons that match the existing speed HUD.

## Momentum 0.1.9-unofficial.1 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port of Momentum for Automobility.

### Changed
- Increased the cruise control HUD icon size and added a procedural fallback so the cruise indicator remains visible if texture rendering fails in the HUD layer.

## Momentum 0.1.8-unofficial.1 - 2026-06-27

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port of Momentum for Automobility.

### Changed
- Replaced the procedural cruise control HUD glyph with a dedicated texture icon.

## Momentum 0.1.7-unofficial.1 - 2026-06-26

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port of Momentum for Automobility.

### Added
- Added cruise control on the `C` key while driving an Automobility vehicle.
- Added cruise control HUD indication to the existing Momentum speed HUD, including target speed and active/coasting color changes.

### Fixed
- Cruise control now disengages when braking, drifting, reversing, leaving the driver seat, or hitting a wall/vehicle hard enough to count as an impact.
- Cruise throttle now avoids direct speed injection and instead uses normal Automobility forward input so multiplayer behavior stays compatible with the existing vehicle sync.

## Momentum 0.1.5-unofficial.1 - 2026-06-26

Initial unofficial Fabric port of Momentum for Automobility to Minecraft Java 26.1.2.

### Added and Ported
- Ported Momentum's vehicle movement changes, braking, coasting, steering behavior, drift profiles, camera behavior, and minimal speed HUD to Minecraft 26.1.2.
- Ported client-to-server key state sync for brake and drift input on dedicated multiplayer servers.
- Ported the optional YACL config screen. It is available only when YACL is installed.

### Known Limits
- ModMenu integration is not included in this build because the available ModMenu API artifact for this environment is not compatible with the named Loom dependency setup used by this port.
- This addon requires Automobility `0.5.0-unofficial.2` or newer on both client and server.

## 0.5.0-unofficial.2 - 2026-06-26

Patch release for the unofficial Minecraft Java 26.1.2 Fabric port.

### Fixed
- Fixed slope and steep slope block rendering on current Fabric so placed slopes render as sloped geometry instead of full cubes.
- Fixed the grass cutter front attachment so it harvests short grass, tall grass, ferns, dead bush, and flower-tagged blocks while leaving crops to the crop harvester.

## 0.5.0-unofficial.1 - 2026-06-20

Unofficial beta Fabric port of Automobility to Minecraft Java 26.1.2.

### Added and Ported
- Ported the Fabric build to Minecraft 26.1.2, Java 25, Fabric Loader 0.19.2, Fabric API 0.152.1+26.1.2, Gradle 9.6.0, and Loom 1.17.11.
- Restored vehicle rendering, item rendering, OBJ model loading, custom vehicle/resource-pack data, sounds, particles, and recipes on 26.1.2.
- Restored Auto Mechanic Table and Automobile Assembler workflows.
- Restored automobile frames, engines, wheels, front attachments, rear attachments, slopes, dash panels, off-road blocks, launch gel, autopilot sign, pressure plate, and crowbar behavior.
- Ported HUD speedometer placement, boost FOV, drift smoke, Controlify support, horn input, and vehicle audio.

### Fixed
- Fixed multiplayer visual displacement syncing so remote automobiles keep the correct rendered height and tilt when moving between elevations.
- Fixed inventory and held-item rendering paths for automobile parts and attachments on 26.1.2.

### Known Limits
- This is a Fabric-only beta release.
- Broader public modpack compatibility has not been exhaustively tested.
- This is an unofficial fork/port; upstream Automobility support channels should not be used for bugs introduced by this 26.1.2 port.
