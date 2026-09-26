# Momentum .11 for Minecraft 26.3

> **Target-scoped release record:** Only for minecraft_version=26.3.
> Prior public .10 evidence is preserved in docs/release/momentum10-26.3-published.md.

Status: MANUALLY ACCEPTED, NOT YET PUBLISHED. Final player acceptance: 2026-09-26.

- Version number: `0.1.11-unofficial.11+26.3`
- Version type: `beta`
- Automobility Unofficial Port `0.5.0-unofficial.36`: required
- Local JAR: `dist\release-candidate-0.1.11-unofficial.11\momentum-0.1.11-unofficial.11+26.3-fabric.jar`
- Size: `222502` bytes
- SHA-256: `BF62ECE4E632C0C702BF178CA51A841A5F52C8DB8720267FCDD6CFD5E29FFF9F`
- SHA-512: `CBC2E33A09A07ED8F95FAD6FDACC2111F7B49028CFFD278EC50B368C29F86BE61CB69EDB172DCE589F7B879D62C7417962021C03E520ACED2FC9AA6A980A1D05`

## Version Changelog

```markdown
Fix optional Controlify integration for Minecraft 26.3 using the tested 3.5.3 API. Add contextual guides and four cruise-control radial actions; preserve binding IDs and saved custom mappings. No automatic physical-controller assignments.

Requires matching Automobility .36 for 26.3, Java 25, Fabric Loader 0.19.5+ and matching Fabric API 0.161.0+26.3. YACL and Mod Menu remain optional. Core and Momentum still work without Controlify.

This is a new .11 artifact, not a replacement of public .10. The .10 builds for older Minecraft targets already contain the corresponding modern API support, so matching functionality does not require renumbering unchanged binaries.
```

## Evidence and limits

46 Momentum tests passed with zero failures/errors/skips, including 3 new
Controlify resource tests and retained SDL input/key-code regression coverage.
Combined core SDK checks and runtime work are recorded in docs/release/PARITY_26.3.md.
Final packaged NONE/MODERN clients passed with exit 0; modern registration, seven
combined guides/bindings, four radial icons and real resource reload passed.
The user accepted the final production-only manual test on 2026-09-26, including
the requested cruise C/R check. Physical-controller gameplay and two-real-player
coverage remain NOT PROVEN. Publication must pin the newly verified .36+26.3 Modrinth version ID.
