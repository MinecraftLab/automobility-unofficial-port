# Build and source provenance — Minecraft 26.1.2

This is the production source snapshot for the published Automobility
`1.3.0+26.1.2-fabric` file, including integrated Momentum. It is assembled from
the original hash-pinned production inputs, the exact three715 Java overlays,
the approved644 display-name resources, and the final263 bootstrap where applicable.
The source-publication task did not run Gradle, javac, tests or Minecraft.

## Building production code

Use Java25 and the included Gradle wrapper. Set `JAVA_HOME` to that JDK.
Build the integrated Fabric artifact with:

```sh
./gradlew :fabric:jar --no-daemon --max-workers=1 -x test
```

On Windows use `gradlew.bat`. Do not run a standalone Momentum packaging task
for this release. Its module supplies integrated sources and preserves history.
Private native-game fixtures, runtime trees and QA outputs are omitted.

Gradle resolves Minecraft 26.1.2, Fabric Loader/API, Controlify SDKs, YACL,
Mod Menu, Iris/Sodium compile-only APIs and `de.javagl:obj:0.4.0` from the
repositories declared in the checked-in build files. `gradle.properties` pins
the selected versions. Optional SDKs are never bundled. If the original Iris
or Sodium SDK is supplied separately, use
`-PautomobilityIrisSdk=/path/to/iris.jar -PautomobilitySodiumSdk=/path/to/sodium.jar`.
No private Gradle cache, machine path or mutable mod/library hardlink is needed.

## Exact released archive

The release used a Gradle base artifact followed by narrow javac/package
overlays; a clean full compilation may differ in constant-pool ordering or
debug metadata. No byte-for-byte rebuild is asserted by this source export.
`published-members.json` records the released payloads and
`published-fabric.mod.json` the final loader metadata. `source-origin.json`
records the recovered source input hashes without publishing private QA paths.
The immutable published artifact is identified by its SHA256/SHA512 in
`artifact.json`. Never label a different rebuilt JAR as those bytes.

Garage integration binds specific P&P and Powerworks release artifacts in
`GarageOwnerBootstrap`; those constants are part of this exact release.
Updating peer versions is a separate coordinated release operation.
