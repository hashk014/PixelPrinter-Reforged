"""Produce the distributable, source bundle, compatibility report and evidence archive."""

import hashlib, json, pathlib, shutil, struct, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
VERSION = "2.0.0"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package():
    jar = ROOT / "target" / f"PixelPrinter-Reforged-{VERSION}.jar"
    sha = digest(jar)
    data = json.loads(
        (ROOT / "docs/compatibility-results.json").read_text(encoding="utf-8")
    )
    results = data["results"]
    expected = [
        v
        for group in json.loads((ROOT / ".tools/paper.json").read_text())[
            "versions"
        ].values()
        for v in group
        if "-" not in v
    ]
    assert {r["version"] for r in results} == set(expected), "Incomplete version matrix"
    assert all(
        r["status"] == "passed" and not r["errors"] for r in results
    ), "A runtime test failed"
    assert all(
        r["pluginSha256"] == sha for r in results
    ), "Tests used a different artifact"
    with zipfile.ZipFile(jar) as z:
        assert not any(n.startswith("me/zombie_striker/") for n in z.namelist())
        assert (
            z.read("fr/hash014/pixelprinter/PixelPrinterReforged.class")[:8]
            == b"\xca\xfe\xba\xbe\x00\x00\x00\x34"
        )
        for name in [
            "plugin.yml",
            "config.yml",
            "LICENSE",
            "NOTICE",
            "licenses/GSON-LICENSE.txt",
            "images/hash014.png",
            "images/hash014.gif",
        ]:
            assert name in z.namelist(), name
    assert (ROOT / "licenses/GSON-LICENSE.txt").is_file()
    shutil.copy2(ROOT / ".tools/paper.json", ROOT / "docs/paper-catalog.json")
    latest = next(r for r in results if r["version"] == "26.3")
    checks = sum(r["checks"] + r["playerChecks"] + 1 for r in results)
    report = [
        f"# Compatibilité — PixelPrinter Reforged {VERSION}",
        "",
        "**Made by hash014.** Rapport du 11 octobre 2026.",
        "",
        f"**{len(results)}/{len(results)} versions Paper exécutées avec succès**, avec le même JAR (SHA-256 `{sha}`). {checks} contrôles dans les serveurs, en plus des 29 tests unitaires.",
        "",
        "Chaque version a été démarrée réellement dans un dossier jetable. Les vérifications ont posé et annulé des blocs dans le monde, testé la transparence, la couleur legacy, les inventaires des coffres, les GIF, les directions, les statues et les cartes. La région inspectée a été maintenue chargée ; sur 1.7.10, les cadres ont été observés après 125 ticks pour inclure leur contrôle physique. Un client protocole connecté a testé les permissions, les protections événementielles et les murs de cartes dans les quatre orientations, puis chaque serveur a été arrêté et redémarré pour vérifier le GIF et son état original.",
        "",
        f"Minecraft 26.3 a été testé avec Paper build {latest['paperBuild']} **{latest['channel']}**, Java {latest['java']} et un client protocole utilisant le registre de paquets réel du serveur. Les avertissements Windows OSHI/JNA de ce serveur sont distincts des erreurs du plugin ; ils n’ont pas empêché les tests.",
        "",
        "La liste couvre les versions de sortie publiées dans le catalogue Paper obtenu pour ce travail. Les microversions Minecraft absentes de ce catalogue, les snapshots/RC et les autres distributions ne sont pas certifiés par cette matrice. Le plugin vise Bukkit/Spigot/Paper de 1.7.10 à 26.3 ; aucun test Fabric/Forge/Folia ou Bedrock natif.",
        "",
        "Les clients utilisés sont des sondes automatisées. Aucune revue graphique humaine, charge de milliers de joueurs ou combinaison exhaustive avec les plugins tiers n’est revendiquée. Les tests de protection annulent de vrais événements Bukkit, et ne remplacent pas un test de configuration WorldGuard/équivalent sur le serveur cible.",
        "",
        "| Minecraft | Build Paper | Canal | Java testé | Monde | Joueur | Reboot GIF | Paquets carte |",
        "| --- | ---: | --- | ---: | ---: | ---: | --- | ---: |",
    ]
    for r in results:
        report.append(
            f"| {r['version']} | {r['paperBuild']} | {r['channel']} | {r['java']} | {r['checks']} | {r['playerChecks']} | OK | {r['mapPackets']} |"
        )
    report += [
        "",
        "Les valeurs Java sont celles utilisées pour les tests, pas une promesse que toutes les autres JVM sont acceptées. Le build historique 1.17 exige Java 16 ; les versions récentes peuvent exiger Java 25.",
        "",
        "Fichiers de preuve : `compatibility-results.json`, `PixelPrinter-Reforged-2.0.0-test-evidence.zip` et `target/surefire-reports`. Le nombre de paquets de cartes peut être nul sur le client 1.7.10 ; les objets de carte, les renderers, leur sérialisation et les cadres ont été validés par l’API serveur.",
        "",
        "Un probe distinct a validé la récupération réelle d’un profil et de son skin auprès de Mojang avec le JAR livré, sous Java 8 et Java 25 (voir `skin-probe.txt`).",
        "",
        "Sources techniques : [PixelPrinter original](https://github.com/ZombieStriker/PixelPrinter), [Paper 26.3](https://papermc.io/news/26-3/), [service de téléchargement Paper](https://docs.papermc.io/misc/downloads-service/), [Java pour Paper](https://docs.papermc.io/misc/java-install/).",
        "",
    ]
    (ROOT / "docs/COMPATIBILITY.md").write_text("\n".join(report), encoding="utf-8")
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    shutil.copy2(jar, dist / jar.name)
    shutil.copy2(ROOT / "licenses/GSON-LICENSE.txt", dist / "GSON-LICENSE.txt")
    for file in [
        "README.md",
        "README.fr.md",
        "PROJECT_DESCRIPTION.md",
        "CONTRIBUTING.md",
        ".editorconfig",
        ".gitattributes",
        "LICENSE",
        "NOTICE",
        "docs/COMPATIBILITY.md",
        "docs/compatibility-results.json",
    ]:
        shutil.copy2(ROOT / file, dist / pathlib.Path(file).name)
    (dist / "README.md").write_text(
        (ROOT / "README.md")
        .read_text(encoding="utf-8")
        .replace("(docs/COMPATIBILITY.md)", "(COMPATIBILITY.md)"),
        encoding="utf-8",
    )
    shutil.copy2(
        ROOT / "target" / f"PixelPrinter-Reforged-{VERSION}-sources.jar",
        dist / f"PixelPrinter-Reforged-{VERSION}-sources.jar",
    )
    files = []
    for folder in ["src", "docs", "tools", "licenses", ".github"]:
        files.extend(
            p
            for p in (ROOT / folder).rglob("*")
            if p.is_file() and "__pycache__" not in p.parts
        )
    files.extend(
        ROOT / f
        for f in [
            "README.md",
            "README.fr.md",
            "PROJECT_DESCRIPTION.md",
            "CONTRIBUTING.md",
            ".editorconfig",
            ".gitattributes",
            "LICENSE",
            "NOTICE",
            "pom.xml",
            "build.ps1",
            ".gitignore",
        ]
    )
    with zipfile.ZipFile(
        dist / f"PixelPrinter-Reforged-{VERSION}-source.zip", "w", zipfile.ZIP_DEFLATED
    ) as z:
        for p in sorted(files):
            z.write(p, p.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(
        dist / f"PixelPrinter-Reforged-{VERSION}-test-evidence.zip",
        "w",
        zipfile.ZIP_DEFLATED,
    ) as z:
        z.write(ROOT / "docs/compatibility-results.json", "compatibility-results.json")
        z.write(ROOT / "docs/paper-catalog.json", "paper-catalog.json")
        z.write(ROOT / "docs/skin-probe.txt", "skin-probe.txt")
        for r in results:
            folder = ROOT / "test-servers" / r["version"]
            for f in ["reforged-test.log", "client-test.log", "download.json"]:
                z.write(folder / f, r["version"] + "/" + f)
        for p in (ROOT / "target/surefire-reports").glob("*"):
            z.write(p, "unit-tests/" + p.name)
    with zipfile.ZipFile(
        dist / f"PixelPrinter-Reforged-{VERSION}-complete.zip",
        "w",
        zipfile.ZIP_DEFLATED,
    ) as z:
        z.write(dist / jar.name, jar.name)
        z.write(
            dist / f"PixelPrinter-Reforged-{VERSION}-test-evidence.zip",
            f"tests/PixelPrinter-Reforged-{VERSION}-test-evidence.zip",
        )
        for file in [
            "README.md",
            "README.fr.md",
            "PROJECT_DESCRIPTION.md",
            "CONTRIBUTING.md",
            ".editorconfig",
            ".gitattributes",
            "LICENSE",
            "NOTICE",
            "GSON-LICENSE.txt",
            "COMPATIBILITY.md",
            "compatibility-results.json",
        ]:
            z.write(dist / file, file)
        for p in sorted(files):
            z.write(p, "source/" + p.relative_to(ROOT).as_posix())
        for p in (ROOT / "src/main/resources/images").iterdir():
            z.write(p, "examples/" + p.name)
    hashes = []
    for p in sorted(dist.iterdir()):
        if p.is_file() and p.name != "SHA256SUMS.txt":
            hashes.append(digest(p) + "  " + p.name)
    (dist / "SHA256SUMS.txt").write_text("\n".join(hashes) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "jar": str(dist / jar.name),
                "sha256": sha,
                "versions": len(results),
                "runtimeChecks": checks,
                "unitTests": 29,
                "files": [(p.name, p.stat().st_size) for p in dist.iterdir()],
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    package()
