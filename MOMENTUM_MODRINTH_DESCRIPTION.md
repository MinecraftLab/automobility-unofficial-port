# Momentum for Automobility: Unofficial Port

This is an unofficial Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) by milkucha for newer Minecraft versions. It requires [Automobility: Unofficial Port](https://modrinth.com/mod/automobility-unofficial-port); it does not include the base vehicle mod.

Momentum adjusts Automobility's acceleration, coasting, braking, steering, drift behavior, and camera response. It also adds a compact speed HUD and cruise control.

## Features

- Smoother acceleration, coasting, and braking
- Touring, Balanced, Sport, and Custom driving tunes, with hand-tuned chassis values
  preserved when switching between them
- Separate brake and handbrake/drift inputs
- Multiple drift profiles and drift skid sounds
- Camera response for steering, braking, reversing, and drifting
- Compact pixel dashboard with exact speed, graphical km/h unit, speed/boost segments,
  and a persistent cruise-control target marker
- Optional legacy bar-and-text HUD through the in-game configuration
- Cruise control with set/cancel, adjustable target speed, and resume; every binding
  is remappable in Minecraft's Controls screen
- Native optional Controlify support for all four cruise actions, with a contextual
  controller guide and radial-menu entries; no physical controller buttons are claimed
  by default
- Dedicated multiplayer synchronization for brake, drift, and gameplay configuration
- Clear read-only server gameplay settings in multiplayer while camera and HUD
  preferences remain personal
- Complete localized options, notifications and debug HUD in the same 17 languages
  as the Automobility port

Cruise control disengages when braking, drifting, reversing, leaving the driver seat, or hitting a wall or vehicle hard enough to count as an impact. Its last target remains available to Resume until the driver leaves the vehicle.

## Supported Versions

The September 26 Momentum `.10` beta release line targets Minecraft Java **26.1.2**
and **26.2**, with a separate Fabric file for each. Install the Momentum and
Automobility files for the same Minecraft target. Other Minecraft releases have
separate files and dependency requirements.

## Requirements

- Fabric Loader and Fabric API
- [Automobility: Unofficial Port](https://modrinth.com/mod/automobility-unofficial-port) `0.5.0-unofficial.35` for the matching Minecraft target, on both client and server, for Momentum `.10` on 26.1.2 or 26.2
- Java 25 or newer

Momentum is required on both the client and server in multiplayer. Movement, steering, drift, and cruise settings are controlled by the server; HUD, camera, and key preferences remain local to each client.

Server operators can inspect and update Momentum without a restart using `/momentum status`,
`/momentum reload`, `/momentum sync`, and `/momentum preset touring|balanced|sport`.
Reloads and preset changes are synchronized immediately with every compatible connected client.

## Optional

- YetAnotherConfigLib (YACL) enables the in-game options screen.
- Mod Menu exposes the Momentum options button when YACL is also installed.
- Controlify `3.0.2` or `3.5.3`, using its matching Minecraft build, adds native
  controller bindings, contextual guide hints, and four radial-menu cruise actions.
  These are the exact SDK/API versions covered by the build and regression checks;
  arbitrary newer SDKs are not promised compatible. Neither SDK is bundled.

Controlify remains optional. Separate adapters preserve existing binding IDs;
unknown API families disable the optional integration and retain keyboard controls.

## Compatibility

Earlier port versions were tested in singleplayer and on a multiplayer Fabric
26.1.2 server with a large set of performance, world-generation, mapping, voice-chat,
controller, rendering, shader, and quality-of-life mods. Those reports are historical,
not fresh acceptance of the September 26 betas or a guarantee for every modpack.

The new build gate passed **119 tests per target** (82 core, 37 Momentum) on
26.1.2 and 26.2. Target-specific runtime results are recorded separately; a successful
build is not a runtime pass. Fresh physical-controller gameplay, two-real-player
driving, and full-modpack acceptance remain **NOT PROVEN**. Include logs and a full
mod list when reporting an issue.

This project is not affiliated with or endorsed by the upstream Momentum maintainer unless stated otherwise by that maintainer. Report bugs specific to this port on this project's issue tracker rather than upstream support channels.
