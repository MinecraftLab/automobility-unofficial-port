# Verified Minecraft 26.3 parity release — 2026-09-26

User accepted the final requested production-only manual test with
"Ano je to vše ok". Publication followed that acceptance; public availability
was independently checked after upload, not inferred from local builds.

## Public versions

| Project | Version | Modrinth ID | Required core |
| --- | --- | --- | --- |
| Automobility | 0.5.0-unofficial.36+26.3 | [yPZ9P5d8](https://modrinth.com/mod/automobility-unofficial-port/version/yPZ9P5d8) | — |
| Momentum | 0.1.11-unofficial.11+26.3 | [uIpEAR2D](https://modrinth.com/mod/momentum-for-automobility-unofficial-port/version/uIpEAR2D) | yPZ9P5d8 |
| RIAutomobility | 0.1.0-unofficial.6+26.3 | [8hM0nOi2](https://modrinth.com/mod/riautomobility-unofficial-port/version/8hM0nOi2) | yPZ9P5d8 |

Each is listed beta, Fabric only, Minecraft 26.3 only, client_and_server.
Version pages returned HTTP 200. Fresh anonymous CDN downloads matched size,
SHA-256 and SHA-512. Production JARs contain no QA probes.

RIA was not re-uploaded: only its exact required core dependency and corresponding
compatibility text changed. Before/after metadata and unchanged binary hashes
were verified; other metadata and dependencies were preserved. Older Auto34 and
Momentum10 versions were not edited or removed.

## Artifact identities

- Auto36: 1,419,185 bytes; SHA-256 `63C4A547764254663B6DD53D3594E1AF761BE8B5B86D4E250A36E9612315BC3A`.
- Momentum11: 222,502 bytes; SHA-256 `BF62ECE4E632C0C702BF178CA51A841A5F52C8DB8720267FCDD6CFD5E29FFF9F`.
- RIA6: 596,259 bytes; SHA-256 `82E1E2046F9E16E6E6C8DE2A13E20D00F6B13D61140ABF762FCB72E4521F4015`.

Both source-owned artifact records contain SHA-512 as well. Release source is
[7d3b1449cdd34f72375472ae30827ca811749a84](https://git.offchunk.com/Offchunk/automobility-unofficial-port/commit/7d3b1449cdd34f72375472ae30827ca811749a84).
Follow-up publication documentation does not change those tested binaries.

## Consistent release lanes

The final public parity gate ran WITHOUT PendingMinecraft or ReportOnly and
passed all nine rows:

| Minecraft | Automobility | Momentum | RIA |
| --- | --- | --- | --- |
| 26.1.2 | .36 | .10 | .6 |
| 26.2 | .36 | .10 | .6 |
| 26.3 | .36 | .11 | .6 |

The older Momentum .10 builds already contain modern Controlify API support;
their unchanged binaries do not need renumbering to achieve functional parity.
Five common policy files are byte-identical across all three release checkouts.
Older-lane policy commits are `8ff5c7420f5b582afe2cd66dc291483016c8f2d1` (26.1.2)
and `f39f5e0b0024466cefca79828cd9272af737337a` (26.2), verified on Offchunk.

## Local immutable evidence

Repository `dist/publication-yPZ9P5d8.json` and
`dist/parity-public-final-20260926.json` preserve core publication and the final
full public matrix. Exact addon before/after receipts and fresh downloaded bytes:

`C:/Users/david/Automobility-36-Parity-QA-20260926/release/addons263-20260926-202531-06d8f32a2c654d1d9ff0eb075986a454`

Separate anonymous-only verification:

`C:/Users/david/Automobility-36-Parity-QA-20260926/release/addons263-20260926-202555-641ff523cc8a43a9a1e7727d8b1116d0`

Reviewed addon publisher SHA-256:
`5825DD09DC58A282E794FD175DFD4538087D1FC352CBC87E4C7A53C5CC903746`.

179 unit tests and 59 release-policy tests passed. Packaged runtime evidence is
separate from user-reported manual acceptance; see PARITY_26.3.md.
Physical-controller gameplay, two-real-player and exhaustive lifecycle coverage
remain NOT PROVEN. No measured FPS gain is claimed. No P&P, adapter, Autoradio or
Roadworks 26.3 release is implied by this milestone.
