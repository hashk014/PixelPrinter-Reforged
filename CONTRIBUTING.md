# Contributing

PixelPrinter Reforged is maintained by hash014. Preserve the original PixelPrinter attribution and the GPL-2.0-or-later license when contributing or distributing changes.

## Build and unit tests

Use JDK 17 or newer and Maven 3.9:

```sh
mvn clean verify
```

The plugin is compiled for Java 8. GitHub Actions builds with Java 21 and also runs the unit tests on Java 8. These checks cover compilation and the core image-processing code; they do not replace real Minecraft server tests.

## Source layout

| Path | Contents |
| --- | --- |
| `src/main/java/fr/hash014/pixelprinter/` | Plugin commands, image loading, palettes, build scheduler, maps, skins, persistence, and compatibility adapters. |
| `src/main/resources/` | Plugin metadata, default configuration, example images, and bundled license notices. |
| `src/test/java/` | Core unit tests. |
| `tools/` | Portable Windows tooling, server downloads, protocol clients, test runners, and release packaging. |
| `docs/` | Historical compatibility results, Paper catalog snapshot, and Mojang probe evidence. |
| `licenses/` | Third-party license text. |

Keep world changes on the Bukkit server thread. Image decoding and network access belong in worker tasks. Preserve processing budgets, permissions, protection events, and memory limits.

Use capability checks for APIs that differ between server versions. Avoid direct dependencies on Minecraft server classes. The guarded 1.7 frame-attachment adapter is documented in the implementation and notice.

## Real server tests on Windows

The current runtime test tools use portable Windows JDKs, Python 3.10 or newer, Node.js, and disposable Paper servers. The Minecraft servers bind to `127.0.0.1`.

```powershell
python tools/bootstrap.py
./build.ps1
python tools/prepare_servers.py --all
python tools/cache_vanilla.py
npm install --prefix .tools/protocol minecraft-protocol@1.68.0 minecraft-data@3.117.0
python tools/run_matrix.py --all --workers 3
```

The runner accepts the Minecraft EULA for its disposable test servers. Read the [Minecraft EULA](https://www.minecraft.net/en-us/eula) before running it. Each fresh test deletes and recreates only its test worlds and plugin data beneath `test-servers`; its reboot check preserves the first boot's data.

The internal server-test commands require `-Dpixelprinter.reforged.test=true`. Use them only on disposable servers. Historical results in `docs/compatibility-results.json` identify the exact tested JAR by SHA-256. A new build or changed behavior needs its own evidence before claiming the same runtime coverage.

`python tools/package_release.py` packages a release only when the complete recorded matrix passes and every result matches the current JAR's SHA-256. It requires the downloaded catalog and local server logs.

## Reporting an issue

Include the Minecraft version, server distribution and build, Java version, Reforged version, command used, and relevant console output. Explain what you expected and what happened. For an image or GIF issue, attach a reproducible sample you are allowed to share.
