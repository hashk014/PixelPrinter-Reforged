from pathlib import Path

for p in Path("src").rglob("*.java"):
    text = p.read_text(encoding="utf-8")
    if not text.startswith("/* SPDX-"):
        p.write_text(
            "/* SPDX-License-Identifier: GPL-2.0-or-later\n * Copyright (C) 2026 hash014\n */\n"
            + text,
            encoding="utf-8",
        )
