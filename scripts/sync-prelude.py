#!/usr/bin/env python3
"""Embeds scripts/jsonui-prelude.js into the Swift and Kotlin sources.

Run after editing the prelude:  python3 scripts/sync-prelude.py
"""
import pathlib

root = pathlib.Path(__file__).resolve().parent.parent
prelude = (root / "scripts" / "jsonui-prelude.js").read_text()

swift_target = root / "ios" / "Sources" / "JsonUICore" / "ScriptPrelude.swift"
kotlin_target = root / "android" / "jsonui-core" / "src" / "main" / "kotlin" / "com" / "bclnet" / "jsonui" / "ScriptPrelude.kt"

header = "GENERATED FILE - do not edit. Source: scripts/jsonui-prelude.js (run scripts/sync-prelude.py)."

swift_target.parent.mkdir(parents=True, exist_ok=True)
swift_target.write_text(
    "//\n//  ScriptPrelude.swift\n//  JsonUI\n//\n//  " + header + "\n//\n\n"
    "import Foundation\n\n"
    "/// The JavaScript runtime installed into every script engine before the document script.\n"
    "public enum JsonScriptPrelude {\n"
    "    public static let source: String = #\"\"\"\n" + prelude + "\"\"\"#\n"
    "}\n"
)

kotlin_target.parent.mkdir(parents=True, exist_ok=True)
kotlin_target.write_text(
    "// " + header + "\n"
    "package com.bclnet.jsonui\n\n"
    "/** The JavaScript runtime installed into every script engine before the document script. */\n"
    "object ScriptPrelude {\n"
    "    const val SOURCE: String = \"\"\"" + prelude.replace("$", "${'$'}") + "\"\"\"\n"
    "}\n"
)
print("wrote", swift_target.relative_to(root))
print("wrote", kotlin_target.relative_to(root))
