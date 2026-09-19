# Animal boarding regression fixture

This opt-in Fabric server fixture validates the actual production AutomobileEntity
and Minecraft passenger methods. Its classes and entrypoint are separate from the
production JAR. **Run it only in a disposable dedicated-server directory**: it
places a small stone pad, creates test entities, and stops the server when finished.

## Build and run

1. Set JAVA_HOME to Java 25, then run
   `gradlew.bat :fabric:animalBoardingQaJar --max-workers=2`.
2. Prepare a fresh, loopback-only Fabric dedicated server for the branch's Minecraft
   target with Fabric API, the exact built Automobility `.34` JAR and
   `fabric/build/qa/automobility-animal-boarding-qa.jar`.
3. Start the server with Java 25 and a 2 GiB maximum heap. Retain its normal console
   input so a failed startup can still be stopped normally.
4. Require `AUTO34_ANIMAL_BOARDING_PASS checks=22`, no
   `AUTO34_ANIMAL_BOARDING_FAIL`, and a normal save/stop. Exit 0 alone is insufficient.
5. Save the complete log and compare the installed production JAR hash with the
   staged candidate before recording a pass.

Never include the QA JAR in an actual modpack, upload, or player installation.
It is not built by the normal production `build` task.

## Coverage

- Normal admission and startRiding reject 17 real animal types without partially
  modifying either side of the passenger relationship: pig, cow, sheep, chicken,
  wolf, cat, horse, bee, fox, rabbit, frog, axolotl, cod, squid, glow squid,
  dolphin and bat.
- A touching pig stays outside while a touching villager still auto-boards.
- The real ServerPlayer admission check accepts empty driver and rear seats and
  respects the existing one-seat/two-seat capacity.
- An animal cannot occupy the free rear passenger attachment; a second villager can.
- A car with two villagers and two forcibly mounted animals is serialized to NBT
  and loaded through vanilla's recursive entity loader. Its next tick dismounts both
  animals alive while retaining both villagers. This tests NBT reconstruction, not
  a complete process restart or multiplayer rendering.

The 26.2 fixture uses EntityTypes constants; 26.1.2 uses EntityType constants.
The production animal filter is identical in both targets, including Animal,
WaterAnimal, AgeableWaterCreature and AmbientCreature subclasses.

## Local evidence

Isolated server directories:
`C:\Users\david\Automobility-34-Animal-Boarding-QA\26.2` and
`C:\Users\david\Automobility-34-Animal-Boarding-QA\26.1.2`.
Successful logs are retained as `animal-boarding-pass.log` in each directory.

Both targets passed 22 scenario groups on 2026-09-12 and stopped with exit 0.
SHA-256 of the retained logs:

- 26.2: `2F8F1F81309E334B441FFEEA131DDDA991B94FA0069C1DD0D5254C21573FC374`
- 26.1.2: `8A62B2FDC81AB59FA17CE9289A2917FF56236F5152F417395F4C20C24E3843BA`

The existing Windows OSHI/Perflib counter diagnostic can appear during startup.
It does not replace checking the fixture's explicit pass marker and clean stop.

## Offchunk metadata rebuild, 26.1.2 (2026-09-19)

The final release candidate changes only the source/issues URLs in `fabric.mod.json`
relative to the earlier artifact; all other 760 ZIP entries are byte-identical.
The installed production SHA-256 is
`7301339B4A1C350D03C3247E69840FDC82114ED18ECA44362F7FC7F3BA452BBE`.
The original production/fixture JARs and original pass log are retained in the QA
directory's `archive-20260912-pre-offchunk` folder; the original pass log also remains
at its original path.

A fresh world selected using `--world animal-boarding-offchunk-20260919-fresh` passed
all 22 groups and saved/stopped with exit 0. The loopback port 25662 was no longer
listening after exit. Retained log:
`C:\Users\david\Automobility-34-Animal-Boarding-QA\26.1.2\animal-boarding-offchunk-20260919-fresh.log`.
Log SHA-256: `E26AE1A4A9B050CD86664593A029A2BC55109F5EE467EEDA96C27BC007933797`.

The first attempt reused the previous fixture world and **failed** after 19 checks
at `touching villager still auto-boards`; that run is not counted as a pass. Its log
is retained as `animal-boarding-offchunk-20260919.log`, SHA-256
`A448C311003E1AAF3B024349678FD6E95B9DE77BCE234B83042CE67846E7358B`.
The precise startup-state cause was not established. The fixture requires the fresh
world specified above; neither this test nor its NBT roundtrip establishes complete
process-restart, multiplayer, or GUI/manual-driving acceptance.
