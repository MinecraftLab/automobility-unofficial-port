# Offchunk source publication

Applies to Automobility (including integrated Momentum), Pumps & Pistons,
Powerworks and Autoradio. David approved source publication on 23 October 2026.

## Everyday development

Use the project's curated source checkout and a `dev/<minecraft>` branch.
At each meaningful completed change, commit the intended source files explicitly.
Do not use blanket `git add .` on experimental workspaces. The installed
post-commit hook pushes that committed branch to the existing Offchunk origin
and checks the remote object ID. Failed push means a saved local commit, not a
published checkpoint; resolve it before reporting the handoff complete.

The helper never stages files, copies dependencies, builds, starts a game,
force-pushes, rewrites history or publishes a Modrinth release. Frozen source
lanes remain read-only. Source-only lanes without Git must export the exact
changed source files into the curated checkout at their completed checkpoint.
Keep a source manifest when a build came from a snapshot or an assembly step.

Keep active development separate from released code. `main` contains the
project introduction, current release index, build instructions and source
navigation. `dev/26.1.2`, `dev/26.2` and `dev/26.3` are explicitly unfinished.
Version-specific release tags point to immutable exact source snapshots.

## Every release

1. Commit the exact source/build inputs used for the candidate, including
   required generated-source recipes, dependency versions and license notices.
   A later approximation is not an exact release source.
2. Create and push the version/MC tag without moving any existing public tag.
3. Record version, Minecraft target, source commit, tag, artifact filename and
   SHA-512 in `releases/source-index.json`. Include Modrinth version ID once
   assigned and source-file hashes where recovery came from pinned snapshots.
4. Before the Modrinth upload, verify the public tag and exact candidate file:

   `python -B tools/source_sync.py verify-release --repo . --manifest releases/source-index.json`

5. After upload, add the real Modrinth ID, rerun the same read-only verification
   against the public API and update README/current release links. The source
   record and artifact mapping must be complete before declaring publication
   finished. No extra gameplay tests are part of this source check.

Historical versions with unrecoverable exact inputs must have an explicit
gap record. Do not relabel a nearby commit as exact and do not silently move
an old tag. Public music/artwork retain their specific redistribution rights;
never include private masters, credentials, player files or local runtime data.

## Installation in a curated checkout

Copy `tools/source_sync.py` to `tools/source_sync.py` and `templates/post-commit`
to `.githooks/post-commit`. Preserve any existing hook by composing it rather
than overwriting it. Set repository-local `core.hooksPath=.githooks`,
`offchunk.sourceSync=true`, `offchunk.sourceSyncPython` to the installed Python
executable and `offchunk.sourceSyncHelper` to the checkout's helper path. Git
Credential Manager keeps credentials outside source. This hook is event-based;
it does not add a scheduled polling task.
