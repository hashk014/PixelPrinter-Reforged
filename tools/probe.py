import urllib.request, json, pathlib, zipfile

root = pathlib.Path(".tools")
root.mkdir(exist_ok=True)
headers = {
    "User-Agent": "PixelPrinterReforged/2.0 (https://github.com/hashk014/PixelPrinter-Reforged)"
}
for name, url in [
    ("paper", "https://fill.papermc.io/v3/projects/paper"),
    ("builds263", "https://fill.papermc.io/v3/projects/paper/versions/26.3/builds"),
    (
        "adoptium",
        "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows",
    ),
    (
        "spigot",
        "https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.8.8-R0.1-SNAPSHOT/maven-metadata.xml",
    ),
]:
    try:
        data = urllib.request.urlopen(
            urllib.request.Request(url, headers=headers), timeout=30
        ).read()
        (root / (name + ".json" if name != "spigot" else name + ".xml")).write_bytes(
            data
        )
        print(name, data[:1200].decode())
    except Exception as e:
        print(name, repr(e))
