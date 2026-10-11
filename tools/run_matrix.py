"""Actual isolated loopback Paper boots and in-world regression checks."""

import argparse, concurrent.futures, hashlib, json, pathlib, queue, re, shutil, subprocess, threading, time
import socket, sys
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAR = ROOT / "target" / "PixelPrinter-Reforged-2.0.0.jar"


def runtime(v):
    if v == "1.17":
        return 16
    if v.startswith("26."):
        return 25
    parts = tuple(map(int, v.split(".")))
    return 8 if parts < (1, 17) else 17 if parts < (1, 20, 5) else 21


def test(version, port, restart=False):
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    folder = ROOT / "test-servers" / version
    if not (folder / "server.jar").exists():
        return dict(version=version, status="unavailable")
    plugins = folder / "plugins"
    plugins.mkdir(exist_ok=True)
    shutil.copy2(JAR, plugins / JAR.name)
    # Repeat runs use clean disposable worlds and plugin state; restart checks later preserve the first boot.
    for name in (
        "test-world",
        "test-world_nether",
        "test-world_the_end",
        "plugins/PixelPrinterReforged",
    ):
        target = (folder / name).resolve()
        assert target.is_relative_to((ROOT / "test-servers").resolve())
        if target.exists():
            shutil.rmtree(target)
    (folder / "eula.txt").write_text("eula=true\n")
    (folder / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nallow-flight=true\nlevel-name=test-world\nlevel-type=flat\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=4\nallow-nether=false\nspawn-npcs=false\nspawn-monsters=false\nspawn-animals=false\ngenerate-structures=false\nenable-query=false\nenable-rcon=false\nnetwork-compression-threshold=-1\n"
    )
    (folder / "bukkit.yml").write_text(
        "settings:\n  allow-end: false\n  connection-throttle: 0\nspawn-limits:\n  monsters: 0\n  animals: 0\n  water-animals: 0\n  ambient: 0\n"
    )
    (folder / "spigot.yml").write_text(
        "world-settings:\n  default:\n    verbose: false\n    mob-spawn-range: 0\n    view-distance: 2\n    merge-radius:\n      item: 0\n      exp: 0\n"
    )
    (folder / "paper.yml").write_text(
        "world-settings:\n  default:\n    keep-spawn-loaded: false\n    keep-spawn-loaded-range: 0\n"
    )
    config = folder / "config"
    config.mkdir(exist_ok=True)
    (config / "paper-world-defaults.yml").write_text(
        "spawn:\n  keep-spawn-loaded: false\n  keep-spawn-loaded-range: 0\n"
    )
    java = ROOT / ".tools" / f"jdk{runtime(version)}" / "bin" / "java.exe"
    cmd = [
        str(java),
        "-Xms128M",
        "-Xmx768M",
        "-XX:ActiveProcessorCount=2",
        "-Dfile.encoding=UTF-8",
        "-Dpixelprinter.reforged.test=true",
        "-jar",
        "server.jar",
        "nogui",
    ]
    started = time.monotonic()
    log = []
    lines = queue.Queue()
    status = "timeout"
    checks = 0
    playerChecks = 0
    sent = False
    ready = False
    bot = None
    botLog = None
    phase = "selftest"
    p = subprocess.Popen(
        cmd,
        cwd=folder,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )

    def pump():
        for line in p.stdout:
            lines.put(line)
            log.append(line)

    thread = threading.Thread(target=pump, daemon=True)
    thread.start()
    try:
        while time.monotonic() - started < 240:
            try:
                line = lines.get(timeout=0.5)
            except queue.Empty:
                if p.poll() is not None:
                    status = "boot-failed"
                    break
                if phase == "player" and bot is not None and bot.poll() is not None:
                    status = "client-failed"
                    break
                continue
            if ("Done (" in line or "Done (" in "".join(log[-2:])) and not sent:
                ready = True
                sent = True
                p.stdin.write("pp selftest\n")
                p.stdin.flush()
            if "SELFTEST PASS" in line:
                checks = int(re.search(r"checks=(\d+)", line).group(1))
                botLog = open(folder / "client-test.log", "w", encoding="utf-8")
                if version in ("26.2", "26.3"):
                    p.stdin.write("pp protocolinfo\n")
                    p.stdin.flush()
                    phase = "protocol"
                else:
                    bot = subprocess.Popen(
                        [
                            "node",
                            str(ROOT / "tools" / "test_client.cjs"),
                            str(port),
                            version,
                        ],
                        cwd=ROOT,
                        stdout=botLog,
                        stderr=subprocess.STDOUT,
                    )
                    phase = "player"
            if "PROTOCOL INFO READY" in line:
                bot = subprocess.Popen(
                    [
                        sys.executable,
                        str(ROOT / "tools" / "raw_client.py"),
                        str(port),
                        version,
                        str(plugins / "PixelPrinterReforged" / "protocol.json"),
                    ],
                    cwd=ROOT,
                    stdout=botLog,
                    stderr=subprocess.STDOUT,
                )
                phase = "player"
            if (
                phase == "player"
                and "Hash014Test" in line
                and "logged in with entity id" in line
            ):
                p.stdin.write("pp playercheck Hash014Test\n")
                p.stdin.flush()
            if "PLAYERTEST PASS" in line:
                playerChecks = int(re.search(r"checks=(\d+)", line).group(1))
                phase = "fixture"
                p.stdin.write("pp persistfixture\n")
                p.stdin.flush()
            if "PERSISTENCE FIXTURE READY" in line:
                status = "first-boot-passed"
                break
            if "SELFTEST FAIL" in line or "PLAYERTEST FAIL" in line:
                status = "test-failed"
                break
    finally:
        if p.poll() is None:
            try:
                p.stdin.write("stop\n")
                p.stdin.flush()
                p.wait(timeout=40)
            except (OSError, subprocess.TimeoutExpired):
                p.kill()
                p.wait()
        thread.join(timeout=5)
        if bot is not None and bot.poll() is None:
            bot.terminate()
            bot.wait(timeout=5)
        if botLog is not None:
            botLog.close()
    if status == "first-boot-passed":
        p = subprocess.Popen(
            cmd,
            cwd=folder,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        lines = queue.Queue()
        thread = threading.Thread(target=pump, daemon=True)
        thread.start()
        restartStarted = time.monotonic()
        try:
            while time.monotonic() - restartStarted < 120:
                try:
                    line = lines.get(timeout=0.5)
                except queue.Empty:
                    if p.poll() is not None:
                        status = "restart-failed"
                        break
                    continue
                if "GIF restaur" in line and "ppr-persist.gif" in line:
                    p.stdin.write("pp persistcheck\n")
                    p.stdin.flush()
                if "PERSISTENCE PASS" in line:
                    status = "passed"
                    break
                if "PERSISTENCE FAIL" in line:
                    status = "persistence-failed"
                    break
            else:
                status = "restart-timeout"
        finally:
            if p.poll() is None:
                try:
                    p.stdin.write("stop\n")
                    p.stdin.flush()
                    p.wait(timeout=40)
                except (OSError, subprocess.TimeoutExpired):
                    p.kill()
                    p.wait()
            thread.join(timeout=5)
        (folder / "reforged-test.log").write_text("".join(log), encoding="utf-8")
    else:
        (folder / "reforged-test.log").write_text("".join(log), encoding="utf-8")
    failures = [
        s.strip()
        for s in log
        if any(
            k in s
            for k in [
                "SELFTEST FAIL",
                "PLAYERTEST FAIL",
                "Could not load",
                "Could not pass event",
                "Error occurred while",
                "NoSuchMethodError",
                "UnsupportedClassVersionError",
            ]
        )
    ]
    if failures and status == "passed":
        status = "test-failed"
    environmental = [s.strip() for s in log if "Win32Exception" in s]
    clientText = (
        (folder / "client-test.log").read_text(encoding="utf-8", errors="replace")
        if (folder / "client-test.log").exists()
        else ""
    )
    maps = len(re.findall("CLIENT MAP", clientText))
    build = json.loads((folder / "download.json").read_text())
    result = dict(
        version=version,
        status=status,
        checks=checks,
        playerChecks=playerChecks,
        mapPackets=maps,
        persistence=status == "passed",
        pluginSha256=hashlib.sha256((plugins / JAR.name).read_bytes()).hexdigest(),
        java=runtime(version),
        paperBuild=build["id"],
        channel=build["channel"],
        seconds=round(time.monotonic() - started, 1),
        ready=ready,
        errors=failures[-8:],
        environmentWarnings=environmental[:1],
        log=str((folder / "reforged-test.log").relative_to(ROOT)),
    )
    print(json.dumps(result), flush=True)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("versions", nargs="*")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--workers", type=int, default=2)
    args = parser.parse_args()
    versions = args.versions or sorted(
        p.name for p in (ROOT / "test-servers").iterdir() if (p / "server.jar").exists()
    )
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(test, v, 25600 + i) for i, v in enumerate(versions)]
        for f in concurrent.futures.as_completed(futures):
            results.append(f.result())
            (ROOT / "docs" / "compatibility-results.json").write_text(
                json.dumps(
                    {
                        "testedAt": datetime.now(timezone.utc).isoformat(),
                        "results": sorted(
                            results,
                            key=lambda x: tuple(map(int, x["version"].split("."))),
                        ),
                    },
                    indent=2,
                ),
                encoding="utf-8",
            )
