# Drivetrain development source checkpoint

This dev branch contains the selected production source families of the next
Core1.4 drivetrain update, including integrated Momentum, profile definitions,
garage/physical owner APIs and cabin metadata. The curated Gradle export is
**NOT_REBUILT**. It is an unfinished source checkpoint, not a release or a new
gameplay result. Existing private build evidence does not qualify these newly
exported Gradle inputs.

Use Java25 and the matching `dev/<minecraft>` branch. Production common APIs
and pure mechanics live in `common`, loader/platform code in `fabric`, and
Momentum keeps its normal sources. Fabric compiles/merges the Momentum classes
and resources into the OUTER Core mod. There is no nested Momentum JAR to
install. Optional rendering/controller SDKs remain compile-only.

The normal Fabric build creates an **UNBOUND intermediate** containing
`META-INF/automobility/drivetrain-owner-seals.properties`. Its new owner hashes
are deliberately UNBOUND and runtime admission fails closed. Keep this
intermediate out of `mods`; it is a compile SDK and canonical binding input.
The actual source build command is the ordinary matching Gradle task:

```text
./gradlew :fabric:remapJar
```

Build the matching P&P1.12 outer mod with its exact local Core SDK/SHA. The
compat module must be embedded once and never installed separately. Then use
Powerworks' public `tools/drivetrain/cohort.py` with explicit three-target
Core/P&P inputs to generate PW OwnerInputs and its standalone P&P table.
See that repository's `docs/DRIVETRAIN-SOURCE-BUILD.md` for executable commands.
The toolkit is plain Python3.11+ and uses no developer-specific path, runtime
cache, private coordinator record, game process or network downloader.

The canonical Core digest excludes exactly the one owner DATA resource.
Bootstrap CODE, classes, metadata, mixins and every other file stay included;
ten independent required member hashes are also bound by PW. After PW builds,
the public `seal` command creates NEW Core JARs with the exact eight-key owner
data for the actual P&P whole/nested and PW whole hashes. It verifies that all
other members and the canonical content remain unchanged. Inputs are never
edited. A changed Core content or P&P build requires a fresh PW binding/build.
This acyclic flow does not embed a final whole-Core hash back into PW.

`verify` proves static archive/content/hash edges and binding-constant presence,
not JVM ABI, loaded-origin admission or gameplay. Actual loader admission still
applies in the game. Do not weaken717 historical policies or treat an UNBOUND
resource as a launchable cohort. A source checkpoint commit is not a release
tag, source-index change or Modrinth publication.

No private QA/runtime/world/dependency tree belongs in this source export.
Locale files are unchanged; ongoing localization is separate.
