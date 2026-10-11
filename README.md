# PixelPrinter Reforged

[![Build](https://github.com/hashk014/PixelPrinter-Reforged/actions/workflows/build.yml/badge.svg)](https://github.com/hashk014/PixelPrinter-Reforged/actions/workflows/build.yml)

[French guide](README.fr.md) · [Project description](PROJECT_DESCRIPTION.md) · [Compatibility report](docs/COMPATIBILITY.md)

**Made by hash014 · Version 2.0.0**

PixelPrinter Reforged turns images, animated GIFs, and Minecraft skins into creations inside a Minecraft Java server. Build pixel art from blocks, display images across maps and item frames, or generate three-dimensional player statues directly through in-game commands.

Reforged is a complete reimplementation of the original [PixelPrinter project by Zombie_Striker](https://github.com/ZombieStriker/PixelPrinter). It brings the core features into a new codebase with version adapters, configurable processing limits, persistent animations, and extensive testing across historical and current Minecraft server versions.

It runs as a **Bukkit, Spigot, or Paper server plugin**. Players do not need to install a client mod.

## What you can create

### Images made from blocks

Convert PNG, JPEG, BMP, and GIF files into Minecraft pixel art. Choose the height and orientation while preserving the image's proportions. Transparent pixels can leave the existing world untouched, and optional dithering improves the appearance of gradients when working with a limited block palette.

The palette adapts to the materials available on the server, including wool, terracotta, concrete, stone, wood, and supported newer blocks. Administrators can restrict the materials or supply PNG textures and resource-pack ZIP files to adjust the colors used for matching.

### Animated GIFs

Create animated block displays that follow the GIF's frame timing. Active block animations can be paused, resumed, stopped, or undone.

Active GIF builds save their animation descriptors and original blocks. With restoration enabled, they can resume after a server restart once their owner is online. Their source files must remain available and unchanged.

### Maps and item-frame walls

Render images and GIFs onto Minecraft maps. Large images are split into 128-pixel tiles, and the map-wall command builds the support and places the frames automatically.

Map images, renderers, and animations are stored persistently. Animated maps use their own rendering system; the command that stops block GIFs does not stop map animations.

### Player skin statues

Generate a three-dimensional statue from a player name, UUID, or local skin PNG. The skin builder supports classic and modern skin layouts, clothing overlays, remotely identified slim models, and available remote capes. Statue scale can be set from 1 to 4.

## One JAR across server versions

The compatibility target is **Minecraft Java 1.7.10 through 26.3**, using a single plugin JAR:

```text
PixelPrinter-Reforged-2.0.0.jar
```

The plugin adapts to the capabilities of each server rather than requiring a separate download for every Minecraft version. The server still needs its appropriate Java runtime. **Minecraft 26.3 was tested with Java 25.**

The release was validated on **all 55 release versions available in the Paper catalog used for this project**, spanning 1.7.10 to 26.3. Minecraft microversions absent from that catalog are not certified by these tests. Bukkit and Spigot are intended platforms; the runtime test matrix was executed on Paper.

Fabric, Forge, Folia, and native Bedrock servers are outside the supported platform scope.

## Build management and server controls

Reforged processes construction in scheduled batches. The default global budget is **200 positions per tick**, with a **6 ms processing target**, shared between builds, block animations, and undo operations. These settings are adjustable; synchronous chunk loading can still exceed the target time.

Administrators can configure image sizes, download sizes, frame counts, active jobs, stored map pixels, and undo history. Image loading and network downloads run in worker threads, while world changes run on the server thread.

Permissions control creation, downloads, deletion, job management, and administration. Modification permissions default to server operators. Placement and undo operations trigger Bukkit block-placement events, and frame placement triggers hanging-placement events, allowing protection plugins to reject changes. Compatibility with a particular protection plugin and configuration should be checked on the target server.

Remote HTTP and HTTPS downloads have size limits, timeouts, and address checks at each redirect. Local and private addresses, URLs containing credentials, and file paths outside the image directory are rejected. An optional hostname allowlist provides additional control.

The plugin includes no telemetry, automatic updater, or dependency downloads at startup.

## Getting started

1. Stop the server and remove the original PixelPrinter JAR if installed; both plugins use `/pp`.
2. Place `PixelPrinter-Reforged-2.0.0.jar` in the server's `plugins` directory.
3. Start the server. Reforged creates its configuration and installs two example images.
4. Add your images to `plugins/PixelPrinterReforged/images`.
5. Stand at the lower-left starting point of your build and run a command.

```text
/pp create South hash014.png 32 true true
/pp create FlatNorthEast hash014.gif 24
/pp map hash014.png 2
/pp mapwall North hash014.png 2
/pp createSkin West Notch 2
/pp undo
```

Use `/pp help` for the in-game command list. `/ppr`, `/pixelprinter`, and `/pixelprinterreforged` are also available as command names.

Images from an existing PixelPrinter installation can be copied into the new image directory. URL references stored in `.txt` files are recognized. The original plugin's serialized GIF saves and undo histories are not imported.

Undo history for completed static builds, statues, and map walls is kept for the current server session and is lost after a restart. Persistent maps and active block GIF restoration are handled separately.

## Validation

The version 2.0.0 validation completed on **October 11, 2026**, using the same release JAR throughout:

| Validation | Result |
| --- | --- |
| Paper server versions tested | 55 / 55 passed |
| Unit tests | 29 passed |
| In-server checks | 2,648 passed |
| Minecraft 26.3 test build | Paper 174, BETA |
| Java runtime for 26.3 | Java 25 |

Tests launched real isolated servers, connected protocol clients, placed and restored blocks, exercised permissions and protection events, checked map walls in all four orientations, and restarted each server to verify block-GIF persistence. A separate probe verified live Mojang profile and skin retrieval under Java 8 and Java 25.

These results cover automated server behavior. They do not represent a human visual review, a large multiplayer load test, or exhaustive testing with third-party plugins. Full version and build details are available in the [compatibility report](docs/COMPATIBILITY.md).

## Building from source

Use **JDK 17 or newer** and **Maven 3.9**:

```sh
mvn clean verify
```

The build produces the plugin JAR and source JAR in `target/`. The plugin targets Java 8 bytecode; use the Java runtime required by your Minecraft server when installing it.

On Windows, `./build.ps1` can use the portable JDK and Maven downloaded by `python tools/bootstrap.py`, or your own paths supplied with `-JavaHome` and `-Maven`.

GitHub Actions builds the project and runs the unit tests on Java 21 and Java 8. Successful runs provide the plugin and source JARs as build artifacts. The historical 55-version server matrix is separate from these CI checks.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the source layout and server-test workflow. Downloaded runtimes, Minecraft servers, disposable worlds, and generated build files are excluded from Git.

## Open-source release

**PixelPrinter Reforged is developed by hash014 and released under GPL-2.0-or-later.** Attribution to the original PixelPrinter project is retained. Gson is bundled under its Apache 2.0 license.

The complete release includes the plugin JAR, source code, build and test scripts, examples, installation guide, licenses, compatibility report, and test evidence.

**PixelPrinter Reforged — made by hash014.**
