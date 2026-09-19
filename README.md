![Automobility](./md/banner.png)

# Automobility: Unofficial Port

> **26.3 development branch:** this branch contains an unpublished, core-only
> Automobility candidate for Minecraft 26.3. See [PORTING_26.3.md](PORTING_26.3.md)
> for its build commands, verified server checks, and remaining client gates.
> Momentum and optional addon compatibility are not covered by this port stage.
> The combined release gate below is not yet configured for 26.3; the remaining
> release-support information describes the existing 26.1.x/26.2 releases.

This repository is an unofficial Fabric port/fork of [Automobility](https://modrinth.com/mod/automobility) by [FoundationGames](https://github.com/FoundationGames/Automobility) for current Minecraft Java 26.x releases. The `main` branch targets 26.1.x and is built and smoke-tested on 26.1.2; the `port/26.2` branch targets 26.2. It is not affiliated with or endorsed by FoundationGames unless stated otherwise by the upstream maintainers.

Automobility adds customizable and functional vehicles, automobile parts, attachments, road-building blocks, driving controls, sounds, and resource/data-pack vehicle support.

**Automobility is currently in BETA.** This port should also be treated as beta until it has wider public testing.

## Available for Fabric
- Supports Minecraft Java **26.1.x** and **26.2** through separate matching builds
- 26.1.x release builds are smoke-tested on **26.1.2**
- Requires **[Fabric API](https://modrinth.com/mod/fabric-api)** matching the Minecraft build
- Requires Fabric Loader **0.19.2+ for 26.1.x** or **0.19.3+ for 26.2**
- Requires Java **25 or newer**
- Optional Fabric controller support through **[Controlify](https://modrinth.com/mod/controlify)**

This port currently publishes a Fabric build only. The upstream project is maintained separately; use this fork's issue tracker for bugs specific to this port.

## Contributor QA

Run the same local/CI gate from PowerShell before handing a build to testers:

```powershell
.\tools\qa\Test-Automobility.ps1
```

The script finds the repository's local Temurin JDK 25 fallback, or accepts an explicit
JDK with `-JavaHome C:\path\to\jdk-25`. It validates release-facing files, runs a clean
unit-test/build pass, and checks the final Automobility and Momentum JAR names, Fabric
metadata, referenced resources and entrypoints, embedded `LICENSE`/`NOTICE.md`, ZIP
paths, duplicates, development-file leaks, the embedded Obj license, and the active
Automobility and Momentum release records' exact JAR sizes, SHA-256 and SHA-512 hashes.
The local default gate also verifies that both separately staged `Local JAR` files are
byte-identical to the fresh builds. Use `-Rebuild` to disable Gradle's build cache and
force every task for a release-candidate pass. It never publishes or uploads.

When preparing a new, unpublished version, run with `-PrepareReleaseRecord` once to print
the newly built sizes, SHA-256 and SHA-512 hashes without accepting them as final. Update
the matching `MODRINTH_RELEASE_<minecraft>.md` and
`MOMENTUM_MODRINTH_RELEASE_<minecraft>.md`, copy both fresh JARs to the exact ignored
`Local JAR` paths recorded there, then rerun without that switch. The default command is
the final local upload gate. CI explicitly uses `-CiBuildVerification`: it reproduces both
builds and checks them against the committed hashes, but cannot verify ignored local staging
copies. Preparation mode is disabled in CI; never replace an already-published version's
record with different bytes.

Dedicated-server startup plus two-client driving, tracking, attachment, persistence,
restart, and disconnect tests are still manual release-candidate gates; this repository
does not yet have a safe automated multiplayer runtime harness.

## Momentum Addon
This repository also contains an unofficial 26.x Fabric port of [Momentum for Automobility](https://modrinth.com/mod/momentum-for-automobility) in the `momentum` subproject.

Momentum is an optional Automobility addon that changes vehicle movement feel and adds a compact speed HUD with cruise control. It requires this Automobility port on both client and server. YACL and Mod Menu are optional; install both to open Momentum's configuration from Mod Menu.

## Port Status
- Vehicle driving, drifting, horn, sounds, speed HUD, boost FOV, and drift smoke are ported.
- Automobile Assembler, Auto Mechanic Table, recipes, parts, attachments, and road blocks are ported.
- OBJ-based vehicle/resource-pack rendering and addon data are ported.
- Multiplayer vehicle visual displacement is synced so remote cars keep correct height and tilt when changing elevation.
- Vehicle state packets are validated, rate-limited, and sent only to clients that are actually tracking the automobile.
- Momentum synchronizes server-authoritative gameplay settings and remote brake/drift state while keeping HUD, camera, and key settings client-side.
- Momentum cruise control supports target adjustment and resume, while server operators can reload,
  sync, or switch driving tunes live without restarting players.

### Supports Data Packs and Resource Packs
- Add your own custom vehicle types with data packs and resource packs! 
- Here is an [example](https://github.com/FoundationGames/City-Vehicles-Example-Addon) that you can use as a template.

## Getting Started
- **Recipes:** Crafting table recipes can be viewed using [**EMI**](https://www.curseforge.com/minecraft/mc-mods/emi) or [**JEI**](https://www.curseforge.com/minecraft/mc-mods/jei). Automobile part recipes are viewed in the Auto Mechanic Table's GUI.
- **Automobile Parts:** Craft an Auto Mechanic Table. Use the GUI to craft the frame, engine, and wheels for your automobile. You can optionally craft an attachment.
- **Building your Automobile:** Craft an Automobile Assembler, as well as a Crowbar. Place parts on the assembler until the vehicle is complete. Use a crowbar to destroy the vehicle.
- **Enhancing your Automobile:** Use your Auto Mechanic Table to craft attachments, which can be placed on your Automobile to add exciting functionality or utility.
- **Building:** You can use Slopes, Dash Panels, Off-Road tiles and more to build roads, racetracks, or obstacle courses.

![Automobile Construction](./md/construction.png)
![Automobile Types](./md/parking.png)

## Driving
- W - Accelerate
- S - Brake/Reverse/Burnout
- A/D - Steer left/right
- Space - Drift/Deploy
- Control - Honk horn

**Controller Support (*Fabric Exclusive*):** when using [Controlify](https://modrinth.com/mod/controlify), you will be able to control automobiles with the following default controls:
- A - Accelerate
- B - Brake/Reverse
- LStick - Steer left/right
- RTrigger - Drift/Deploy

![Driving](./md/driving.png)

### Credit: Audio
All sound effects used (originals licensed under CC0) from [freesound.org](https://freesound.org/): <br/>
- [ENGINE~1.WAV](https://freesound.org/people/MarlonHJ/sounds/242739/) *by MarlonHJ* <br/>
- [Marine diesel engine](https://freesound.org/people/AugustSandberg/sounds/264864/) *by AugustSandberg* <br/>
- [metal_ring_01.wav](https://freesound.org/people/Department64/sounds/95272/) *by Department64* <br/>
- [metalbang0.wav](https://freesound.org/people/SamsterBirdies/sounds/435699/) *by SamsterBirdies* <br/>
- [Hollow Bang](https://freesound.org/people/qubodup/sounds/157609/) *by qubodup* <br/>
- [car park skiding corner.wav](https://freesound.org/people/martian/sounds/178889/) *by martian* <br/>
- [Mini Klaxon Horn "Ahooga" sound](https://freesound.org/people/Mastersoundboy2005/sounds/719876/) *by Mastersoundboy2005* <br/>
- [Honk Alarm Repeat loop.mp3](https://freesound.org/people/bowlingballout/sounds/400894/) *by bowlingballout* <br/>
- [Vuvuzela](https://freesound.org/people/nomerodin1/sounds/557277/) *by nomerodin1* <br/>
- [small horn.wav](https://freesound.org/people/tm1000/sounds/94868/) *by tm1000* <br/>
- [Car horn (Ford Fiesta)](https://freesound.org/people/Jokerman83/sounds/732545/) *by Jokerman83* <br/>
