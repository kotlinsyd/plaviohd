#!/usr/bin/env python3
"""
Generate the per-source PlavioHD CloudStream plugins.

The all-in-one plugin in ../PlavioHD is the template. For every source in
scripts/sources.json this script creates a sibling module
PlavioHD-<Source> that is a copy of the template with three small patches:

  * PlavioSettings.BAKED_SOURCE is set to the source, so the plugin builds
    a /c/ token naming only that source (a pasted personal link in the
    plugin settings still wins, exactly like the all-in-one).
  * The provider's display name becomes the source name, so its streams
    file under the real source in CloudStream.
  * Its settings sheet title/hint describe the single source.

Every patch is an exact-match replacement guarded by a marker; if the
template drifts, this script fails loudly instead of generating a wrong
plugin. Re-running is safe: previously generated modules (recognised by
their GENERATED.md) are removed and rebuilt from the current template.

Usage:  python3 scripts/generate_source_plugins.py
"""

import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "PlavioHD"
DATA = ROOT / "scripts" / "sources.json"

NAME_RE = re.compile(r"^[A-Za-z0-9]+$")


def fail(msg: str) -> "SystemExit":
    return SystemExit(f"generate_source_plugins: ERROR: {msg}")


def replace_exact(text: str, old: str, new: str, where: str) -> str:
    if text.count(old) != 1:
        raise fail(f"expected exactly one occurrence of {old!r} in {where}, "
                   f"found {text.count(old)} — template drifted, update the script")
    return text.replace(old, new)


def replace_regex(text: str, pattern: str, new: str, where: str) -> str:
    out, n = re.subn(pattern, new, text, count=1, flags=re.MULTILINE)
    if n != 1:
        raise fail(f"pattern {pattern!r} not found exactly once in {where} "
                   f"— template drifted, update the script")
    return out


def patch_module(module: Path, name: str) -> None:
    # build.gradle.kts — fresh v1 line, single-source description.
    gradle = module / "build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    text = replace_regex(text, r"^version = \d+$", "version = 1", str(gradle))
    text = replace_exact(
        text,
        'description = "PlavioHD addon inside CloudStream: movies and series '
        'with many sources, powered by the PlavioHD addon."',
        f'description = "PlavioHD addon inside CloudStream: the {name} '
        f'source only, powered by the PlavioHD addon."',
        str(gradle),
    )
    gradle.write_text(text, encoding="utf-8")

    pkg = module / "src/main/kotlin/com/kotlinsyd/plaviohd"

    # PlavioSettings.kt — bake the one source.
    settings = pkg / "PlavioSettings.kt"
    text = settings.read_text(encoding="utf-8")
    text = replace_regex(
        text,
        r'val BAKED_SOURCE: String\? = null // GENERATED-BAKED-SOURCE',
        f'val BAKED_SOURCE: String? = "{name}" // GENERATED-BAKED-SOURCE',
        str(settings),
    )
    settings.write_text(text, encoding="utf-8")

    # PlavioHDProvider.kt — the provider carries the source's own name.
    provider = pkg / "PlavioHDProvider.kt"
    text = provider.read_text(encoding="utf-8")
    text = replace_regex(
        text,
        r'override var name = "PlavioHD" // GENERATED-PROVIDER-NAME',
        f'override var name = "{name}" // GENERATED-PROVIDER-NAME',
        str(provider),
    )
    provider.write_text(text, encoding="utf-8")

    # strings.xml — settings sheet speaks about the single source.
    strings = module / "src/main/res/values/strings.xml"
    text = strings.read_text(encoding="utf-8")
    text = replace_exact(
        text,
        '<string name="settings_title">PlavioHD settings</string>',
        f'<string name="settings_title">PlavioHD · {name} settings</string>',
        str(strings),
    )
    text = replace_exact(
        text,
        '<string name="settings_hint">Pick your sources and qualities right '
        'here — no configure page needed. Or paste your own configured '
        'PlavioHD link above (the part before /manifest.json): a filled '
        'link always wins, with all your configure-page choices. Clear '
        'the field to use the pickers.</string>',
        f'<string name="settings_hint">This plugin carries the {name} '
        f'source only. Pick your qualities right here, or paste your own '
        f'configured PlavioHD link above (the part before '
        f'/manifest.json): a filled link always wins, with all your '
        f'configure-page choices. Clear the field to use {name} only.'
        f'</string>',
        str(strings),
    )
    strings.write_text(text, encoding="utf-8")

    (module / "GENERATED.md").write_text(
        f"# Generated module — do not edit by hand\n\n"
        f"This is the per-source **{name}** plugin, generated from the "
        f"`PlavioHD` template by `scripts/generate_source_plugins.py`.\n"
        f"Edit the template (or `scripts/sources.json`) and re-run the "
        f"script instead.\n",
        encoding="utf-8",
    )


def main() -> int:
    data = json.loads(DATA.read_text(encoding="utf-8"))
    sources = data["sources"]
    names = [s["name"] for s in sources]
    if len(names) != len(set(names)):
        raise fail("duplicate source names in sources.json")
    for n in names:
        if not NAME_RE.match(n):
            raise fail(f"source name {n!r} is not plain [A-Za-z0-9]+")

    # Remove previously generated modules so dropped sources disappear.
    for child in sorted(ROOT.iterdir()):
        if child.is_dir() and child.name.startswith("PlavioHD-") \
                and (child / "GENERATED.md").exists():
            shutil.rmtree(child)

    for name in names:
        module = ROOT / f"PlavioHD-{name}"
        shutil.copytree(TEMPLATE, module)
        patch_module(module, name)
        print(f"generated PlavioHD-{name}")

    print(f"{len(names)} per-source plugins generated "
          f"(+ the PlavioHD all-in-one template).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
