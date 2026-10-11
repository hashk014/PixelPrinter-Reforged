import urllib.request, json, base64

for name in ["hash014", "Notch"]:
    try:
        lookup = json.load(
            urllib.request.urlopen(
                "https://api.mojang.com/users/profiles/minecraft/" + name, timeout=15
            )
        )
        profile = json.load(
            urllib.request.urlopen(
                "https://sessionserver.mojang.com/session/minecraft/profile/"
                + lookup["id"],
                timeout=15,
            )
        )
        value = next(
            p["value"] for p in profile["properties"] if p["name"] == "textures"
        )
        textures = json.loads(base64.b64decode(value))["textures"]
        print(name, lookup["id"], list(textures), textures["SKIN"].get("metadata", {}))
    except Exception as e:
        print(name, type(e).__name__, str(e))
