# Momentum for Automobility - Unofficial 26.1.x Port

This subproject is an unofficial Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) by milkucha for Minecraft Java 26.1.x. Current builds are built and smoke-tested on Minecraft Java 26.1.2.

Momentum changes Automobility vehicle movement feel and adds a compact driving dashboard. It is optional, but when used in multiplayer it must be installed on both the client and the server.

## Requirements
- Minecraft Java 26.1.x
- Fabric Loader 0.19.2 or newer
- Fabric API matching the 26.1.x build
- Java 25 or newer
- Automobility `0.5.0-unofficial.29` or newer

## Optional
- YACL 3.x for the in-game options screen
- ModMenu is suggested, but direct ModMenu integration is not included in this port build

## Port Status
- Vehicle acceleration, coasting, braking, steering, and drift profiles are ported.
- The compact dashboard shows exact speed, a graphical km/h unit, speed and boost segments,
  plus the selected cruise speed without drawing outside its configured panel.
- The original bar-and-text speed HUD remains selectable as a legacy layout.
- Cruise control is available on the `C` key while driving.
- Brake and drift key state sync works on dedicated servers.
- The YACL options screen can be opened with the Momentum options key when YACL is installed.

This is an unofficial fork/port. Bugs specific to this port should be reported to this fork, not to the upstream Momentum project.
