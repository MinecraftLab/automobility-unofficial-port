# Momentum 26.3 candidate

> **Target-scoped release record:** Only for minecraft_version=26.3.

Status: MANUALLY ACCEPTED BETA / FINAL PUBLICATION CHECKS (2026-09-19).
The user accepted turning and subsequently confirmed the requested cruise,
G seat-switch and cargo checks with this unchanged Momentum artifact in the final
three-mod 26.3 client. Two-real-player and controller coverage remain NOT PROVEN.

- Version number: `0.1.11-unofficial.10+26.3`
- Version type: `beta`
- Game versions: `26.3`
- Loaders: `Fabric`
- Environment: client and server, required on both
- Source: https://git.offchunk.com/Offchunk/automobility-unofficial-port
- Issues: https://git.offchunk.com/Offchunk/automobility-unofficial-port/issues
- Fabric API: required
- Automobility Unofficial Port `0.5.0-unofficial.34`: required
- YACL and Mod Menu: optional, matching 26.3 builds
- Controlify: optional adapter retained, runtime support for 26.3 not proven

## Upload file

- Local JAR: `dist\release-candidate-0.1.11-unofficial.10\momentum-0.1.11-unofficial.10+26.3-fabric.jar`
- Size: `216016` bytes
- SHA-256: `3071A38108E49CB2BAF8B6A56AC0B6E4DFB0F332AA2929A3EB10ABEFB053B4C0`
- SHA-512: `9E6322F78A9129EFE77F95F0B5B5D857DFEABCD97E538B8C41D65581467D9A5D05FAE9E9ACA0C6448CCB9BF33C5C94A0DAC3AE293039D6C7F1D8BF741CC3706C`

## Changes and evidence

Ports Momentum to Minecraft 26.3 including SDL inputs, key names and optional
configuration-screen dependencies. Existing saved key numbers keep their meaning
through an explicit translation layer. No automatic rebinding or new default
controller assignments.

See PORTING_MOMENTUM_26.3.md for exact coverage and limitations. Never replace a
published 26.1.x/26.2 artifact with this target-specific JAR.
