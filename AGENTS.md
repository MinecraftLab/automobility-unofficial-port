# Offchunk public source workflow

Read docs/SOURCE-WORKFLOW.md before source handoffs or releases.
Commit only explicit intended source files on dev/<minecraft>; confirm the
non-forced remote checkpoint before calling a handoff complete.
Publish the exact immutable source tag and artifact mapping before a Modrinth
release, then verify the real public version ID and hashes.
Do not include credentials, private media, player/runtime data or caches.
This source step adds no game/build test, resource grant or Modrinth permission.
