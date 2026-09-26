# Automobility .36 for Minecraft 26.2

> **Target-scoped release record:** Use only with matching minecraft_version.
> Prior public .35 evidence is preserved in docs/release/core35-26.2-published.md.

Status: release candidate. Publication and target installation must be verified separately.

- Version number: `0.5.0-unofficial.36+26.2`
- Local JAR: `dist\release-candidate-0.5.0-unofficial.36\automobility-0.5.0-unofficial.36+26.2-fabric.jar`
- Size: `1416438` bytes
- SHA-256: `E21B4FC8EC54EDC1AC12F7ED0CC9FE2CCD16AC371C1351F60AD6E458618C17FD`
- SHA-512: `15F7BBA4C55C002E941ACF2F69C1148ABAD3B5612B0DACE93CA3381208BDCBB01ABB0A702DE9FFBDE0A259A35589855BF4F7CC03A7C188A6C556B96274D72434`

## Version Changelog

```markdown
Fix parked cars appearing below newly loaded or changed terrain without moving the car. Refresh local terrain displacement after block/chunk/view and frame/wheel changes while preserving remote-driver authority.

Skip already-resolved collision intersections and unnecessary support queries. No movement-packet or saved fuel/NBT format change.

For Pumps & Pistons, use the matching 1.10.1 core and Automobility integration for your Minecraft version. Momentum .10 remains supported. Roadworks is not included or required.
```

## Evidence and limits

100 Automobility and 37 Momentum unit tests per target; production JAR integrity is checked by the default Test-Automobility gate.
The former .36/RC18 regression evidence is at C:/Users/david/Automobility-Ecosystem-Audit-20260926/render-repro-20260926/fix36-validation/.
This candidate normalizes automobility.mixins.json to LF; class files are unchanged from that .36 candidate. The prior binary hash is not reused.
New production-only client/server qualification is kept at C:/Users/david/Rosenvik-Ecosystem-Release-20260926/26.2/candidate.json with exact P&P1.10.1, public Momentum10, RIA6 and Autoradio0.2.0.
Real two-player/controller, extended driving and full-modpack acceptance remain NOT PROVEN. No measured FPS improvement is claimed.
