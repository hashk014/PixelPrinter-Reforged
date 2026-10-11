"""Repair obsolete historical Paperclip download URLs using Mojang's current manifest."""

import concurrent.futures, hashlib, json, pathlib, urllib.request, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent


def get(url):
    return urllib.request.urlopen(url, timeout=120).read()


manifest = json.loads(
    get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
)


def cache(folder):
    with zipfile.ZipFile(folder / "server.jar") as jar:
        if "patch.json" not in jar.namelist():
            return
        patch = json.loads(jar.read("patch.json"))
        version = patch["version"]
    target = folder / "cache" / ("mojang_" + version + ".jar")
    if (
        target.exists()
        and hashlib.sha256(target.read_bytes()).hexdigest().upper()
        == patch["originalHash"].upper()
    ):
        return
    entry = next(e for e in manifest["versions"] if e["id"] == version)
    meta = json.loads(get(entry["url"]))
    download = meta["downloads"]["server"]
    data = get(download["url"])
    assert hashlib.sha1(data).hexdigest() == download["sha1"]
    assert hashlib.sha256(data).hexdigest().upper() == patch["originalHash"].upper()
    target.parent.mkdir(exist_ok=True)
    target.write_bytes(data)
    print(version, "official vanilla cache ready", flush=True)


if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        futures = [
            executor.submit(cache, f)
            for f in (ROOT / "test-servers").iterdir()
            if (f / "server.jar").exists()
        ]
        for f in concurrent.futures.as_completed(futures):
            try:
                f.result()
            except Exception as e:
                print("FAILED", repr(e), flush=True)
