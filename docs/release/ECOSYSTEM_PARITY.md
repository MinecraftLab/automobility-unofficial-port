# One release policy for three Minecraft targets

`tools/qa/ecosystem-matrix.json` is the desired release manifest, not a success
receipt. Keep its identical copies in all three active release lanes synchronized.
The local build gate checks its target's versions and the parity logic tests.
The publication preflight must check the live public matrix before uploading.

| Minecraft | Automobility | Momentum | RIA compatibility baseline |
| --- | --- | --- | --- |
| 26.1.2 | .36 | .10 | .6 |
| 26.2 | .36 | .10 | .6 |
| 26.3 | .36 candidate | .11 candidate | existing .6 |

Same functionality is the goal; do not republish unchanged binaries merely to
equalize numbers. Public .10+26.3 needs a new .11 to receive the modern Controlify
code already present in older-target .10 builds. RIA .8 seat-key candidates for
older targets remain a separate player-acceptance milestone; 26.3 .6 already uses G.

## Required checks

Use PowerShell 7.2 or newer. Windows PowerShell 5.1 is not a supported host for
the shared live parity check.

```powershell
./tools/qa/Test-Automobility.ps1 -Rebuild
./tools/qa/Test-EcosystemParity.ps1
```

The live check uses anonymous Modrinth metadata, selects each latest listed Fabric
version for each exact target, compares desired versions and validates dependency
pins. An old expected release cannot hide a newer untracked release. API failures
fail closed. `-ReportOnly` is diagnostic, never an approval to publish.

During the current target's sequential core/addon publication only, use
`-PendingMinecraft 26.3` (or the checkout's own target). Other targets still must
pass. Pending rows remain visibly unresolved and `publicAligned` remains false.
Run again WITHOUT this option after uploading and fixing addon dependency pins.

For 26.3: publish qualified core36, then Momentum11 pinned to the returned core ID.
After proving unchanged RIA6 against those exact JARs, update its old core34 pin
to the verified core36 ID. Preserve all other metadata, binaries and hashes.
Do not overwrite Momentum10 or public Auto34. Do not alter older accepted files.

Version metadata parity is not proof of binary compatibility, runtime gameplay,
measured performance, public downloaded-byte identity or player acceptance. Record
each separately, with hashes and NOT PROVEN for remaining coverage. P&P, its
adapter, Autoradio and Roadworks are outside this 26.3 milestone.

No scheduler or background monitoring is installed. This is an enforced local
release check, not a promise that unsupported future APIs will work automatically.
