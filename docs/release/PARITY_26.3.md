# Minecraft 26.3 parity qualification - 2026-09-26

Status: implementation, clean no-cache build, default exact artifact gate and
final packaged runtime matrix PASS. Final requested manual gameplay ACCEPTED on
2026-09-26. Publication is still pending until anonymous verification succeeds.

## Scope

- Automobility .36: parked terrain revision hooks, collision early exits and
  modern Controlify API parity. Preserve 26.3 local rotation/remote interpolation.
- Momentum .11: modern Controlify guides, radial actions and guarded dispatch.
- RIA .6: unchanged public binary, compatibility check and dependency-pin plan.
- All three release lanes: common desired version/dependency matrix and gate.

Initial build passed 132 Fabric + 46 Momentum tests, zero failures/errors/skips.
Independent source review found no blocking interaction with the accepted 26.3
driver/interpolation paths. Test fixtures remain excluded from production JARs.

Final default gate: `parity-factory-final-gate-20260926.log`; 179 unit tests pass, exact
staged/built size, SHA-256 and SHA-512 match both release records. Release-policy
logic passes 59 additional deterministic tests. Identical policy/module/wrapper/
test/docs copies are installed in all three release lanes; all local policy checks
pass. The older-target publisher was tested read-only and correctly refuses the
unresolved 26.3 lane before credentials or upload. The current target's pending
preflight is allowed but explicitly does NOT report public parity complete.

Public read-only snapshot: `dist/parity-before-publication-20260926.json`.

The final inventory audit caught one omitted .35 production hunk in
`FabricPlatform.createControlifyController`: detect unsupported SDKs before
controller class loading and catch LinkageError as well as reflection errors.
It is now transferred unchanged and has a compiled-factory regression test.
The first core candidate SHA256 `012F86F9EB6B77AAA99EFAE09FC573AA239903D503F4219F5EE8BA4D87E42FBD`
is retained under `dist/previous-candidates/core36-before-factory-guard-20260926`.
Its earlier runtime passes are historical only, not substituted for reruns of
the final `63C4A547764254663B6DD53D3594E1AF761BE8B5B86D4E250A36E9612315BC3A` core.

The first title-screen QA attempt reported its assertions before resource loading
finished, then suffered a native JVM crash on shutdown. It is NOT counted as a
pass. The QA-only helper now waits for a stable title screen with no loading
overlay before checking and again before requesting exit. Logs/crash evidence
are retained. Production runtime success requires assertions AND normal exit 0.

## Evidence directory

`C:/Users/david/Automobility-36-Parity-QA-20260926`

The isolated QA runner uses fresh directories, exact binary identities and its
own processes. Historical 26.3 QA worlds/receipts and unrelated running games are
not modified. User authorized separate NONE/MODERN Controlify client tests.

Final runtime report: `runtime-results.md` / `runtime-results.json` in that directory.
JSON SHA256: `C5961CBCEE289502AAF96F8FBAB926DA14D58043683BF6FA20D84CF21283A74C`.

| Final case | Observed result |
| --- | --- |
| Dedicated server | 22 animal groups, 5 port, 30 ecosystem, 9 RIA checks plus ExamplePack marker; save/stop and exit 0 |
| Title without Controlify | 2 absence-path checks after stable real title screen; exit 0 |
| Title with Controlify 3.5.3 | 94 checks, 7 bindings, 7 guides, 4 radial icons, initial load plus real resource reload; exit 0 |
| World without Controlify | 46 real packet-handler/interpolation + 21 terrain/frame/wheel/authority checks; exit 0 |
| World with Controlify 3.5.3 | Same 46 + 21 checks; exit 0 |
| Test-world server | Graceful stop via owned QA stop-file hook, saved world, exit 0 |

Every final run verified actual Fabric-loaded origins against final production
hashes. All owned Java processes ended; unrelated processes were only observed,
including a natural exit/restart. Windows Perflib/OSHI, offline Realms/authentication,
YACL and optional SDK environmental warnings remain in the evidence. No claim of
an error-free environment is made. Terrain hook dispatch includes synthetic
callbacks inside the real transformed client, not exhaustive native chunk-packet
ordering. The first failed shutdown and previous-candidate passes are excluded.

## Final manual acceptance

On 2026-09-26 the user replied "Ano je to vše ok" after the production-only
`manual-001` client/server test, using the exact final core/Momentum hashes above
and unchanged public RIA .6. Requested checks were turning/camera, cruise C/R,
G seat switching, lorry cargo and animals beside cars. This is player-reported
acceptance, distinct from automated assertions. No controller or two-player
acceptance is inferred. Parked-terrain checks retain the recorded automated
coverage; exhaustive manual lifecycle coverage is not claimed.

Manual evidence is under `manual/manual-001` and `client/runs/manual-001` in the
QA directory. Five existing cars were positioned by exact UUID in a cloned
showcase world. All 36 original world files were hash-verified unchanged after
launch. No QA mods, exit probes, original-world edits or unrelated-game stops.

## Remaining gates

- Public source/release, ordered core/addon publication, RIA dependency alignment,
  anonymous downloads/hash comparison, and final public parity gate.

Physical-controller gameplay, two-real-player and exhaustive lifecycle coverage
remain NOT PROVEN. No measured FPS gain is claimed. No 26.3 P&P or Autoradio port.
