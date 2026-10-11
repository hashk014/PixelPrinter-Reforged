import urllib.request, json, pathlib, zipfile, hashlib, concurrent.futures

root = pathlib.Path(".tools")
headers = {
    "User-Agent": "PixelPrinterReforged/2.0 (https://github.com/hashk014/PixelPrinter-Reforged)"
}


def get(url):
    return urllib.request.urlopen(
        urllib.request.Request(url, headers=headers), timeout=90
    ).read()


def jdk(v):
    target = root / ("jdk" + str(v))
    if target.exists():
        return
    asset = json.loads(
        get(
            f"https://api.adoptium.net/v3/assets/latest/{v}/hotspot?architecture=x64&image_type=jdk&os=windows"
        )
    )[0]["binary"]["package"]
    data = get(asset["link"])
    assert hashlib.sha256(data).hexdigest() == asset["checksum"]
    z = zipfile.ZipFile(__import__("io").BytesIO(data))
    z.extractall(root / ("java" + str(v)))
    extracted = next((root / ("java" + str(v))).iterdir())
    extracted.rename(target)
    print("JDK", v, "ready", flush=True)


def maven():
    url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
    data = get(url)
    assert hashlib.sha512(data).hexdigest() == get(url + ".sha512").decode().split()[0]
    zipfile.ZipFile(__import__("io").BytesIO(data)).extractall(root)
    print("Maven ready", flush=True)


with concurrent.futures.ThreadPoolExecutor(max_workers=5) as ex:
    futures = [ex.submit(jdk, v) for v in [8, 16, 17, 21, 25]] + [ex.submit(maven)]
    for f in futures:
        f.result()
