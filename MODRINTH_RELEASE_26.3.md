# Automobility .36 for Minecraft 26.3

> **Target-scoped release record:** Only for minecraft_version=26.3.
> Prior public .34 evidence is preserved in docs/release/core34-26.3-published.md.

Status: MANUALLY ACCEPTED, NOT YET PUBLISHED. Final player acceptance: 2026-09-26.

- Version number: `0.5.0-unofficial.36+26.3`
- Version type: `beta`
- Local JAR: `dist\release-candidate-0.5.0-unofficial.36\automobility-0.5.0-unofficial.36+26.3-fabric.jar`
- Size: `1419185` bytes
- SHA-256: `63C4A547764254663B6DD53D3594E1AF761BE8B5B86D4E250A36E9612315BC3A`
- SHA-512: `E2AC7921EC829184AFE30306348B20D7B086DE4ABC06716CA10A49F5D3FCD8F740572EECF5388167B93CB1A670B5D77A6AA19EEE62E216F45CFFA0DB665E0B5A`

## Version Changelog

```markdown
Minecraft 26.3 parity update: refresh parked-car terrain after block, chunk, view, frame and wheel changes. Avoid redundant collision intersections while preserving collision results and remote-driver authority.

Support the tested Controlify 3.5.3 API with modern contextual guides and optional controller integration. Use Momentum .11 for Minecraft 26.3; the older public .10 for this target lacks this modern API integration.

Retain the accepted Minecraft 26.3 local-driver rotation and remote interpolation fixes, no-animal boarding, persistence and security fixes. Existing RIA .6 for 26.3 remains the compatibility target; verify its dependency metadata at publication. No artwork or saved-data format changes.

Requires Java 25, Fabric Loader 0.19.5+ and matching Fabric API 0.161.0+26.3. No 26.3 support for Pumps & Pistons, its adapter, Autoradio or Roadworks is implied.
```

## Evidence and remaining gates

133 core and 46 Momentum unit tests passed with zero failures/errors/skips.
New tests cover terrain/collision parity, compiled client mixin targets, actual
Controlify SDK linkage and modern resources. Existing 26.3 driving/interpolation
regressions remain enabled. No measured FPS improvement is claimed.

Exact runtime and final artifact evidence is tracked in docs/release/PARITY_26.3.md.
All six final packaged runtime runs passed with exit 0 and exact loaded-origin
hashes: dedicated regression, NONE/MODERN title and world clients, and world server.
Local build success is not player acceptance or public availability. Physical
controller, two-real-player and exhaustive lifecycle coverage remain NOT PROVEN.
The user accepted the requested final production-only manual test on 2026-09-26:
turning/camera, cruise C/R, G seats, lorry cargo and animals near cars. Exact tested
artifacts and the isolated manual profile are recorded in docs/release/PARITY_26.3.md.
Do not replace the published .34 artifact. Publish core before Momentum and align
the existing RIA dependency only after compatibility qualification.
