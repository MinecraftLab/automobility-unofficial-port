# Automobility hardening backlog

This file separates release gates for `0.5.0-unofficial.32` from changes that
need a versioned follow-up. It is not a promise that every item belongs in the
same release.

The unpublished `.31` candidate is superseded by `.32` because it can leave a
parked automobile's model at its old altitude after a same-dimension teleport.
Its JARs and release records are retained only as immutable evidence and must not
be uploaded or integrated into a public pack.

## Required before publishing `.32`

- Run the exact release-candidate JARs with two distinct clients and a dedicated
  server. Cover driving input and expiry, observer tracking, run-over damage,
  parked, locally driven and remotely observed automobile teleports, horizontal
  and vertical relocation, dimension transfer, attachment menu removal and drops,
  dismounting, reconnect, save/reload and a full server restart. Preserve both
  client logs and the server log.
- Update Pumps & Pistons/Roadworks from its exact Automobility `.31` dependency
  pin, then test it in a separate lane with `.32`. Do not combine that lane with
  Fuel System while the declared conflict remains.
- Confirm the canonical source and issue URLs are anonymously reachable again.
  Do not replace the flagged account with a duplicate account or repository.
- After an explicitly approved Modrinth upload, download the public artifact and
  verify its SHA-256, anonymous API metadata, CDN response and Maven coordinate
  before releasing dependent mods that require it.

Fuel System `1.8.0` currently pins exact Automobility `.29`. Updating that addon
is ecosystem follow-up work, not a blocker for publishing the standalone
Automobility `.32` files.

## Candidate work for `.33+`

- Give Momentum's brake/drift packet a heartbeat and server-side expiry so a
  frozen client cannot leave either state latched until disconnect.
- Extend numeric validation to programmatic component registration, stream
  codecs and remaining display/geometry values without silently changing valid
  addon tuning. Bound wheelbase cardinality and finite wheel positions/angles,
  validate stream enum ordinals, and cap geometry feeding collision scans.
- Sanitize non-finite persisted and synchronized automobile motion/input state
  at its trust boundaries.
- Preserve unresolved addon component IDs and opaque attachment data when an automobile
  drops its parts, matching the save/load preservation already implemented in `.31`.
- Add entity-level integration tests for tick-driven input expiry, dirty-state
  synchronization, mob/autopilot inputs, corrupt/non-finite state and the
  reverse-to-forward transition. Assert that acceleration and movement stay finite.
- Pin and automate the released RIA `.6` 13-frame/2-wheel registry-codec contract
  that was exercised externally for `.31` and must be repeated for `.32`.
- Design a versioned server-authoritative spatial protocol before removing the
  legacy client position fields. Avoid double simulation and visible jitter for
  remote passengers during migration.
- Profile parked automobiles, hitbox entities and collision-area scans in real
  worlds before changing update frequency or entity structure.
- Remove or tightly scope `mavenLocal()` and add Gradle dependency verification
  and locking for reproducible public builds.
- Define optional protection-mod integrations rather than claiming generic
  claim compatibility from the vanilla spawn-protection checks alone.

## Compatibility constraints

- Keep the legacy 86-byte Automobility packet layout until a versioned protocol
  migration is available; Momentum and other ecosystem code depend on it.
- Do not retune normal vehicle handling in a security patch. Datapack bounds
  should reject unstable values, not clamp accepted values silently.
- Validate 26.2 first, port the same focused patch to 26.1.2, and run the full
  build, artifact and runtime gates independently for both targets.
