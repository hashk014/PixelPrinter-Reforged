"""Fetch official Paper binaries with their SHA-256, no third-party JAR mirrors."""

import argparse, concurrent.futures, hashlib, json, pathlib, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
UA = {
    "User-Agent": "PixelPrinterReforged/2.0 (https://github.com/hashk014/PixelPrinter-Reforged)"
}


def fetch(url):
    return urllib.request.urlopen(
        urllib.request.Request(url, headers=UA), timeout=120
    ).read()


def prepare(version):
    folder = ROOT / "test-servers" / version
    folder.mkdir(parents=True, exist_ok=True)
    if (folder / "server.jar").exists() and (folder / "download.json").exists():
        cached = json.loads((folder / "download.json").read_text())
        expected = cached["downloads"]["server:default"]["checksums"]["sha256"]
        if hashlib.sha256((folder / "server.jar").read_bytes()).hexdigest() == expected:
            return version + " cached (checksum verified)"
    builds = json.loads(
        fetch(
            "https://fill.papermc.io/v3/projects/paper/versions/" + version + "/builds"
        )
    )
    build = next((b for b in builds if b["channel"] == "STABLE"), builds[0])
    download = build["downloads"]["server:default"]
    data = fetch(download["url"])
    assert hashlib.sha256(data).hexdigest() == download["checksums"]["sha256"], (
        version + " SHA-256 mismatch"
    )
    (folder / "server.jar").write_bytes(data)
    (folder / "download.json").write_text(json.dumps(build, indent=2))
    return version + " ready " + str(build["id"]) + " " + build["channel"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("versions", nargs="*")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    versions = args.versions
    if args.all:
        project = json.loads(fetch("https://fill.papermc.io/v3/projects/paper"))
        (ROOT / ".tools").mkdir(exist_ok=True)
        (ROOT / ".tools/paper.json").write_text(
            json.dumps(project, indent=2), encoding="utf-8"
        )
        versions = [
            v for group in project["versions"].values() for v in group if "-" not in v
        ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        futures = {executor.submit(prepare, v): v for v in versions}
        for future in concurrent.futures.as_completed(futures):
            try:
                print(future.result(), flush=True)
            except Exception as e:
                print(futures[future], "FAILED", str(e), flush=True)
