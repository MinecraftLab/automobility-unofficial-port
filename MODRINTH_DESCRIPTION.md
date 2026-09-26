# Automobility: Unofficial Port

This project provides unofficial Fabric ports of [Automobility](https://modrinth.com/mod/automobility) by FoundationGames for Minecraft Java 26.x versions.

Automobility adds customizable and functional vehicles, vehicle parts, attachments, road-building blocks, driving controls, sounds, and data/resource-pack vehicle support.

This is an unofficial fork/port. It is not affiliated with or endorsed by FoundationGames unless stated otherwise by the upstream maintainers. Please report issues for this port on this project's issue tracker, not on upstream Automobility support channels.

## Features

- Customizable automobiles with frames, engines, wheels, and attachments
- Auto Mechanic Table and Automobile Assembler progression
- Drivable vehicles with drifting, horn, sounds, boost FOV, speed HUD, and drift smoke
- Slopes, dash panels, off-road blocks, launch gel, pressure plates, and related road-building tools
- Optional Controlify controller support on Fabric
- Data-pack and resource-pack support for custom vehicles

## Supported Versions

The port supports Minecraft Java 26.1.x, 26.2 and 26.3 on Fabric through separate matching files. The 26.1.x build is built and smoke-tested on 26.1.2. Always select the file for your Minecraft version; a 26.2 JAR is not a 26.3 port.

### Requirements

- A matching Minecraft Java 26.1.x, 26.2 or 26.3 build
- Fabric Loader version required by the selected file
- Fabric API
- Java 25 or newer

### Optional

- Controlify for controller support

### Related Addons

- [Momentum for Automobility: Unofficial Port](https://modrinth.com/mod/momentum-for-automobility-unofficial-port) is an optional addon for this port that adds smoother handling, cruise control, brake/drift input sync, and a compact speed HUD. If used in multiplayer, install it on both the client and the server.
- [RIAutomobility: Unofficial Port](https://modrinth.com/mod/riautomobility-unofficial-port) adds vehicles, seats and lorry cargo through matching target-specific builds.
- Pumps & Pistons and its Automobility adapter, and Autoradio, use separate compatibility/release lanes. Their current 26.1.2/26.2 support does not imply a 26.3 version. Roadworks remains separate.

## Status

This port is beta. Core vehicle gameplay, rendering, sounds, particles, crafting screens, assembler flow, attachments, slopes, and multiplayer vehicle visual sync are working. Network state is validated and distributed using actual entity tracking.

Compatibility testing has been done in singleplayer and on a multiplayer Fabric 26.1.2 server with a large mod set, including common performance mods, world generation mods, mapping mods, voice chat, controller support, shader/renderer mods, and quality-of-life mods. This does not guarantee compatibility with every modpack, so please include logs and a full mod list when reporting issues.
