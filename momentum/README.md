# Momentum for Automobility - Unofficial 26.x Port

<!-- offchunk-player-links:start -->
## For players

| Project | Website & guides | Downloads | Report a bug |
| --- | --- | --- | --- |
| Momentum: Unofficial Port | [Offchunk](https://offchunk.com/mods/momentum) | [Modrinth releases](https://modrinth.com/mod/momentum-for-automobility-unofficial-port/versions) | [Issues](https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues) |

Choose the release for your Minecraft version and follow its listed requirements. Source archives are not installable mod JARs.
This issue tracker is shared: include the mod name in your report.

Include your mod, Minecraft and loader versions, what happened and how to reproduce it. For setup questions or private logs, use [Offchunk Help](https://offchunk.com/help); do not post passwords or access tokens in public issues.
<!-- offchunk-player-links:end -->

This subproject is an unofficial Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) by milkucha. Public Fabric builds are available for Minecraft Java 26.1.2 and 26.2; use the JAR that matches the Minecraft version on both the client and server.

Momentum changes Automobility's acceleration, coasting, braking, steering and drift feel, and adds cruise control plus a compact speed HUD.

## Supported builds

| Minecraft | Momentum build | Declared Automobility minimum | Recommended current pairing |
| --- | --- | --- | --- |
| 26.1.2 | `0.1.11-unofficial.4+26.1.2` | `0.5.0-unofficial.26` | `0.5.0-unofficial.30+26.1.2` |
| 26.2 | `0.1.11-unofficial.4+26.2` | `0.5.0-unofficial.26` | `0.5.0-unofficial.30+26.2` |

The minimum comes from the generated metadata in the published Momentum artifacts. Automobility `.30` is the current recommended pairing, not a stricter hidden requirement.

## Requirements

- Fabric Loader 0.19.2 or newer for Minecraft 26.1.2, or 0.19.3 or newer for Minecraft 26.2
- Fabric API matching the selected Minecraft version
- Java 25 or newer
- A matching Automobility build from the table above

## Optional
- YACL 3.x for the in-game options screen
- ModMenu is suggested, but direct ModMenu integration is not included in this port build

## Beta status

Current public builds are beta on every distribution channel. Core driving behavior, the HUD, cruise control and dedicated-server input synchronization are working, but broader modpack testing is still welcome.

## Port status

- Vehicle acceleration, coasting, braking, steering, and drift profiles are ported.
- Minimal speed HUD is ported.
- Cruise control is available on the `C` key while driving.
- Brake and drift key state sync works on dedicated servers.
- The YACL options screen can be opened with the Momentum options key when YACL is installed.

This is an unofficial fork/port. Bugs specific to this port should be reported to this fork, not to the upstream Momentum project.
