# Automobility: Unofficial Port

Fabric vehicles for Minecraft 26.2, based on FoundationGames' Automobility.
Release **1.3.0+26.2-fabric** includes Momentum driving, cruise control,
the combined vehicle HUD, lights, garage/tuning, and the MC-5, Offroader 90 and W4.
Code by upstream contributors, milkucha and the Offchunk port maintainers.

## Install

Install Java25, Minecraft 26.2, Fabric Loader and the matching Fabric API.
Download the matching file from [Modrinth](https://modrinth.com/mod/automobility-unofficial-port/version/Clo3IK15)
and place it in `mods` on the client and server. Loader/API minima are in
[`release/published-fabric.mod.json`](release/published-fabric.mod.json).
Do not install standalone Momentum alongside this integrated release.
Controlify, Mod Menu, YACL and Iris/shaders are optional; shaders are not
required for the built-in world-surface headlight projection.

## Source and build

This branch is the immutable release source for Minecraft 26.2.
See [build/provenance details](release/BUILD.md), [artifact hashes](release/artifact.json)
and [source inputs](release/source-origin.json). Development belongs on
`dev/26.2`. Released tags are immutable; future or experimental work must never
be added to this tag. Historical standalone Momentum versions remain in this
same repository under `momentum-*` release tags.

## Licence and support

Code retains the MIT licence and original notices. Owned new assets use
CC BY4.0 as listed in `LICENSE-ASSETS.txt`; inherited and third-party material
retains its own notices. See `LICENSE`, `NOTICE.md` and `THIRD_PARTY_LICENSES`.

[Issues](https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues)
· [Automobility releases](https://modrinth.com/mod/automobility-unofficial-port/versions)
· [Historical Momentum](https://modrinth.com/mod/momentum-for-automobility-unofficial-port/versions)
