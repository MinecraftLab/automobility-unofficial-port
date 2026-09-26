# Automobility .36 for Minecraft 26.1.2

> **Target-scoped release record:** Use only with matching minecraft_version.
> Prior public .35 evidence is preserved in docs/release/core35-26.1.2-published.md.

Status: published on 2026-09-26; anonymous CDN size, SHA-256 and SHA-512 verified.
Modrinth version: https://modrinth.com/mod/automobility-unofficial-port/version/8qn3OqtD
Publication receipt: dist/publication-8qn3OqtD.json. Target installations are tracked separately.

- Version number: `0.5.0-unofficial.36+26.1.2`
- Local JAR: `dist\release-candidate-0.5.0-unofficial.36\automobility-0.5.0-unofficial.36+26.1.2-fabric.jar`
- Size: `1416462` bytes
- SHA-256: `CE5A045BB65FB0C4A7367C0D1EAC6D7765155B42ABEB752209333B3AA44D3208`
- SHA-512: `108AEA1B023B133320315680B383D9629F0CDB8BD1DCB4FB7F1538FE91DF746176E19A181278F676BBD5F2066B0C638694F1E4AF33F73DF946BDC73141D68407`

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
New production-only client/server qualification is kept at C:/Users/david/Rosenvik-Ecosystem-Release-20260926/26.1.2/candidate.json with exact P&P1.10.1, public Momentum10, RIA6 and Autoradio0.2.0.
Real two-player/controller, extended driving and full-modpack acceptance remain NOT PROVEN. No measured FPS improvement is claimed.
