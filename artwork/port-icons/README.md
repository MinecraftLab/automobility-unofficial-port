# Port icon masters

The PNG files in `masters/` are the canonical runtime artwork for the shared
unofficial-port upgrade badge family. The Automobility and Momentum subjects
remain distinct; only the small upper-right badge is shared. Automobility is
generated from its pinned original icon plus the badge overlay in `sources/`,
and validation rejects any pixel change outside that badge region.

Synchronize the runtime resources after changing a master:

```powershell
python tools/artwork/port_icon_pipeline.py --sync
```

Run the non-writing release check:

```powershell
python tools/artwork/port_icon_pipeline.py --check
```

The manifest pins the complete PNG SHA-256, dimensions, RGBA badge-region hash,
color format, and required PNG metadata. Intentionally changed artwork therefore
requires an explicit manifest update; old release JARs are never rewritten.
