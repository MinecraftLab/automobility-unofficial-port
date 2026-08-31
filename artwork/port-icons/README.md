# Automobility port icon source

The runtime icon is generated from the pinned original Automobility artwork
plus the shared upper-right upgrade badge in `sources/`. The original subject,
crop, and pixels outside the badge remain unchanged.

Regenerate the canonical master and runtime resource:

```powershell
python tools/artwork/port_icon_pipeline.py --sync
```

Run the non-writing release check:

```powershell
python tools/artwork/port_icon_pipeline.py --check
```

The manifest pins source and output SHA-256 values, dimensions, the exact RGBA
badge region, PNG metadata, changed-pixel count, and diff bounding box. Old
release JARs are never rewritten.
