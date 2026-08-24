# Momentum for Automobility: Unofficial Port

This is an unofficial Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) by milkucha for newer Minecraft versions. It requires [Automobility: Unofficial Port](https://modrinth.com/mod/automobility-unofficial-port); it does not include the base vehicle mod.

Momentum adjusts Automobility's acceleration, coasting, braking, steering, drift behavior, and camera response. It also adds a compact speed HUD and cruise control.

## Features

- Smoother acceleration, coasting, and braking
- Separate brake and handbrake/drift inputs
- Multiple drift profiles and drift skid sounds
- Camera response for steering, braking, reversing, and drifting
- Compact pixel dashboard with exact speed, graphical km/h unit, speed/boost segments,
  and a persistent cruise-control target marker
- Optional legacy bar-and-text HUD through the in-game configuration
- Cruise control on the `C` key while driving
- Controlify support through Automobility's controller bindings
- Dedicated multiplayer synchronization for brake, drift, and gameplay configuration

Cruise control disengages when braking, drifting, reversing, leaving the driver seat, or hitting a wall or vehicle hard enough to count as an impact.

## Supported Versions

Matching Fabric builds are available for Minecraft Java 26.1.x and 26.2. Install the Momentum file that matches both your Minecraft version and the Automobility port file.

## Requirements

- Fabric Loader and Fabric API
- [Automobility: Unofficial Port](https://modrinth.com/mod/automobility-unofficial-port) on both the client and server
- Java 25 or newer

Momentum is required on both the client and server in multiplayer. Movement, steering, drift, and cruise settings are controlled by the server; HUD, camera, and key preferences remain local to each client.

## Optional

- YetAnotherConfigLib (YACL) enables the in-game options screen.
- Mod Menu exposes the Momentum options button when YACL is also installed.

## Compatibility

The port has been tested in singleplayer and on a multiplayer Fabric 26.1.2 server with a large mod set, including common performance, world-generation, mapping, voice-chat, controller, rendering, shader, and quality-of-life mods. This does not guarantee compatibility with every modpack. Include logs and a full mod list when reporting an issue.

This project is not affiliated with or endorsed by the upstream Momentum maintainer unless stated otherwise by that maintainer. Report bugs specific to this port on this project's issue tracker rather than upstream support channels.
