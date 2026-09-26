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

Matching Fabric builds are available for Minecraft Java 26.1.x, 26.2 and 26.3. Install the Momentum file that matches both your Minecraft version and the Automobility port file.

## Requirements

- Fabric Loader and Fabric API
- [Automobility: Unofficial Port](https://modrinth.com/mod/automobility-unofficial-port) on both client and server, matching the minimum version and exact Minecraft target declared by the selected Momentum file
- Java 25 or newer

Momentum is required on both the client and server in multiplayer. Movement, steering, drift, and cruise settings are controlled by the server; HUD, camera, and key preferences remain local to each client.

Server operators can inspect and update Momentum without a restart using `/momentum status`,
`/momentum reload`, `/momentum sync`, and `/momentum preset touring|balanced|sport`.
Reloads and preset changes are synchronized immediately with every compatible connected client.

## Optional

- YetAnotherConfigLib (YACL) enables the in-game options screen.
- Mod Menu exposes the Momentum options button when YACL is also installed.
- Compatible Controlify builds add controller bindings, contextual guide hints and radial-menu actions. Tested API families are version-specific, not a promise of compatibility with arbitrary future SDKs. Older-target Momentum .10 builds support the tested 3.0.2/3.5.3 families; 26.3 modern 3.5.3 support requires the published .11 beta, not its older .10 baseline. Physical-controller gameplay is a separate validation. Momentum remains usable without Controlify.

## Compatibility

The port has been tested in singleplayer and on a multiplayer Fabric 26.1.2 server with a large mod set, including common performance, world-generation, mapping, voice-chat, controller, rendering, shader, and quality-of-life mods. This does not guarantee compatibility with every modpack. Include logs and a full mod list when reporting an issue.

This project is not affiliated with or endorsed by the upstream Momentum maintainer unless stated otherwise by that maintainer. Report bugs specific to this port on this project's issue tracker rather than upstream support channels.
