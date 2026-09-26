# Momentum for Automobility: Unofficial Port

This subproject is an unofficial Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) by milkucha for Minecraft Java 26.x. This branch builds the Minecraft **26.3** candidate; older Minecraft targets use separate builds.

Momentum changes Automobility vehicle movement feel and adds a compact driving dashboard. It is optional, but when used in multiplayer it must be installed on both the client and the server.

## Requirements
- Minecraft Java 26.3
- Fabric Loader 0.19.5+
- Fabric API 0.161.0+26.3 or newer for this Minecraft target
- Java 25 or newer
- The Minecraft 26.3 build of Automobility `0.5.0-unofficial.36` or newer

## Optional
- YACL `3.9.6+26.3-fabric` for the in-game options screen
- Mod Menu `21.0.0-beta.1` exposes Momentum's options button when YACL is also installed
- This Momentum .11 candidate ports the modern adapter for Controlify `3.5.3+mc26.3`.
  SDK linkage and resources have regression tests; current runtime/player status is
  tracked in [parity qualification](../docs/release/PARITY_26.3.md). Do not install a
  26.2 Controlify JAR into 26.3 or override its Minecraft dependency. Neither SDK is bundled.

## Minecraft 26.3 input compatibility

Minecraft 26.3 uses SDL keyboard scancodes. Momentum's registered controls use that API.
Existing `momentum.json` `brakeKey` and `driftKey` numbers intentionally retain their legacy
GLFW meaning (default `83` = S, `32` = Space, `-1` = unbound). They are translated when
polled and displayed, not rewritten when saving, so a shared configuration is not silently
rebound. Letters, digits, punctuation, navigation, modifiers, keypad, and F1-F24 are covered.
The platform-specific legacy `WORLD_1` / `WORLD_2` and F25 have no portable mapping and are
treated as unbound; choose a supported key for those unusual configurations.

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
- When combining with RIAutomobility, keep its seat-switch binding separate from Momentum's
  resume binding. The coordinated 26.3 RIA default is `G`, chosen by the user after finding
  the old `R` collision; existing saved bindings should be checked in Controls.
- On a supported Controlify build, all cruise actions appear in the Driving controller category and can
  be placed on the radial menu. Momentum intentionally leaves physical buttons unassigned by
  default so it never overrides a player's existing gamepad layout.
- Brake and drift key state sync works on dedicated servers.
- Dedicated servers are authoritative for gameplay physics; the options screen makes those controls
  read-only while keeping camera and HUD settings personal to each player.
- Server operators can inspect and update live settings with `/momentum status`,
  `/momentum reload`, `/momentum sync`, and `/momentum preset touring|balanced|sport`.
  Reloads and preset changes are broadcast immediately to compatible connected clients.
- The YACL options screen can be opened with the Momentum options key when YACL is installed.

This is an unofficial fork/port. Bugs specific to this port should be reported to this fork, not to the upstream Momentum project.
