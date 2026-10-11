"""Minimal loopback protocol probe for 26.2/26.3.
Packet IDs come from the actual server ProtocolInfo registry, not an assumed older schema.
Skips world payloads; responds to login/configuration, keepalive and teleport controls.
"""

import json, pathlib, socket, struct, sys, uuid

port, version, protocolFile = sys.argv[1:]
ids = json.loads(pathlib.Path(protocolFile).read_text(encoding="utf-8"))
ROOT = pathlib.Path(__file__).resolve().parent.parent
versions = json.loads(
    (
        ROOT
        / ".tools/protocol/node_modules/minecraft-data/minecraft-data/data/pc/common/protocolVersions.json"
    ).read_text()
)
protocol = next(v["version"] for v in versions if v["minecraftVersion"] == version)


def varint(n):
    n &= 0xFFFFFFFF
    out = bytearray()
    while True:
        byte = n & 127
        n >>= 7
        out.append(byte | (128 if n else 0))
        if not n:
            return bytes(out)


def string(s):
    data = s.encode()
    return varint(len(data)) + data


def readvar(data, offset=0):
    value = 0
    for i in range(5):
        b = data[offset + i]
        value |= (b & 127) << (i * 7)
        if b < 128:
            return value, offset + i + 1
    raise ValueError("invalid varint")


sock = socket.create_connection(("127.0.0.1", int(port)), timeout=20)
sock.settimeout(120)


def packet(id, data=b""):
    body = varint(id) + data
    sock.sendall(varint(len(body)) + body)


def send(state, name, data=b""):
    packet(ids[state + "_serverbound"]["minecraft:" + name], data)


def recvexact(length):
    buffer = b""
    while len(buffer) < length:
        chunk = sock.recv(length - len(buffer))
        if not chunk:
            raise EOFError
        buffer += chunk
    return buffer


packet(
    0, varint(protocol) + string("127.0.0.1") + struct.pack(">H", int(port)) + varint(2)
)
send(
    "login",
    "hello",
    string("Hash014Test") + uuid.UUID("19cd46b4-5227-3575-9cbf-64f1bc95421f").bytes,
)
state = "login"
maps = 0
try:
    while True:
        length = 0
        for i in range(5):
            b = recvexact(1)[0]
            length |= (b & 127) << (7 * i)
            if b < 128:
                break
        data = recvexact(length)
        id, offset = readvar(data)
        payload = data[offset:]
        name = next(
            (
                k.split(":", 1)[1]
                for k, v in ids[state + "_clientbound"].items()
                if v == id
            ),
            "unknown",
        )
        if name in ("disconnect", "login_disconnect"):
            print("CLIENT DISCONNECT", payload.hex(), flush=True)
            break
        if state == "login" and name == "login_finished":
            send("login", "login_acknowledged")
            state = "configuration"
            print("CLIENT LOGIN", version, "protocol=" + str(protocol), flush=True)
            # Minecraft Client Information: locale, view distance, chat, colors, skin bits, main hand, filtering, listing, particles.
            send(
                state,
                "client_information",
                string("en_us")
                + b"\x02"
                + varint(0)
                + b"\x01\x7f"
                + varint(1)
                + b"\x00\x01"
                + varint(0),
            )
        elif name == "select_known_packs":
            send(state, "select_known_packs", varint(0))
        elif name == "code_of_conduct":
            send(state, "accept_code_of_conduct")
        elif name == "finish_configuration":
            send(state, "finish_configuration")
            state = "game"
            print("CLIENT JOIN", flush=True)
        elif name == "keep_alive":
            send(state, "keep_alive", payload)
        elif name == "ping":
            send(state, "pong", payload)
        elif state == "game" and name == "player_position":
            teleport, off = readvar(payload)
            # 26.2+ acknowledgment also returns the absolute position and look angles.
            send(
                state,
                "accept_teleportation",
                varint(teleport)
                + (
                    payload[off : off + 24] + payload[off + 48 : off + 56]
                    if version == "26.3"
                    else b""
                ),
            )
        elif state == "game" and name == "map_item_data":
            maps += 1
            mapid, _ = readvar(payload)
            print("CLIENT MAP", mapid, "packet-bytes=" + str(len(payload)), flush=True)
except EOFError:
    print("CLIENT END socketClosed maps=" + str(maps), flush=True)
finally:
    sock.close()
