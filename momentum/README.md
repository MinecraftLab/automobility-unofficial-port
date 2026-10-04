# Momentum for Automobility: Unofficial Port

This subproject is an unofficial Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) by milkucha. This release line builds Momentum `.10` for Minecraft Java 26.1.2; other Minecraft targets have separate files and requirements.

Momentum changes Automobility vehicle movement feel and adds a compact driving dashboard. It is optional, but when used in multiplayer it must be installed on both the client and the server.

## Requirements

- Minecraft Java 26.1.2
- Fabric Loader 0.19.2 or newer
- Fabric API matching the 26.1.2 build
- Java 25 or newer
- Automobility `0.5.0-unofficial.35` for the same Minecraft target (the minimum core version for this Momentum `.10` build)

## Optional

- YACL 3.x for the in-game options screen
- Mod Menu exposes Momentum's options button when YACL is also installed
- Controlify `3.0.2` or `3.5.3`, using the matching Minecraft build, for native controller bindings, contextual button guides, and radial-menu actions

Those are the exact SDK/API versions covered by the build and regression checks;
compatibility with arbitrary newer SDKs is not promised. Controlify is optional and
neither SDK is bundled. Unknown API families disable the optional integration while
retaining keyboard controls.

## Port Status

- Vehicle acceleration, coasting, braking, steering, and drift profiles are ported.
- Touring, Balanced, Sport, and Custom driving tunes coordinate movement and steering without
  changing drift, camera, HUD, sound, key, or cruise-control preferences. Custom tunes are retained
  while a named preset is active and restored when selected again.
- The compact dashboard shows exact speed, a graphical km/h unit, speed and boost segments,
  plus the selected cruise speed without drawing outside its configured panel.
- The original bar-and-text speed HUD remains selectable as a legacy layout.
- Cruise control uses `C` to set or cancel, `Page Up` / `Page Down` to adjust the
  target, and `R` to resume the last set speed. All four bindings are remappable.
- With a supported Controlify build installed, all cruise actions appear in the Driving controller category and can
  be placed on the radial menu. Momentum intentionally leaves physical buttons unassigned by
  default so it never overrides a player's existing gamepad layout.
- Brake and drift key state sync works on dedicated servers.
- Dedicated servers are authoritative for gameplay physics; the options screen makes those controls
  read-only while keeping camera and HUD settings personal to each player.
- Server operators can inspect and update live settings with `/momentum status`,
  `/momentum reload`, `/momentum sync`, and `/momentum preset touring|balanced|sport`.
  Reloads and preset changes are broadcast immediately to compatible connected clients.
- The YACL options screen can be opened with the Momentum options key when YACL is installed.

## Verification Scope

The September 26 beta build gate passed 119 tests (82 core, 37 Momentum) for each
of Minecraft 26.1.2 and 26.2. Separate adapters retain existing binding IDs across
Controlify 3.0.2 and 3.5.3; the modern adapter provides contextual-guide resources
and radial-menu resources for all four cruise actions.

Target-specific runtime checks are recorded separately and are not claimed passed
by the build results. Fresh physical-controller gameplay, two-real-player driving,
and full-modpack acceptance are **NOT PROVEN** for this beta.

This is an unofficial fork/port. Bugs specific to this port should be reported to this fork, not to the upstream Momentum project.
