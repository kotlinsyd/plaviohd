#!/usr/bin/env python3
"""
Generate the per-source PlavioHD CloudStream plugins.

The plugin in ../PlavioHD is the main all-in-one plugin (all sources, with
the pickers) and also the template for the per-source plugins. For every
source in scripts/sources.json this script creates a sibling module named
exactly after the source (e.g. 4KHDHub, SoraStream) that is a copy of the
template with these small patches:

  * PlavioSettings.BAKED_SOURCE is set to the source, so the plugin builds
    a /c/ token naming only that source (a pasted personal link in the
    plugin settings still wins).
  * The provider's display name becomes the source name, so its streams
    file under the real source in CloudStream.
  * Its settings sheet title/hint describe the single source.
  * Its version is the source's own "version" in sources.json (default 1).
  * Its build.gradle.kts iconUrl: when sources.json gives the source an
    "icon" (a logo file committed in this repo, e.g. icons/4KHDHub.png —
    that source's own site logo), iconUrl points at that file. Sources
    without an "icon" keep the template's iconUrl (the Plavio weave mark)
    untouched.

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

# The Plavio weave mark, committed at the repo root (icon.png on main).
# The template carries this as its iconUrl; per-source modules keep it
# unless sources.json gives them their own icon.
ICON_URL = "https://raw.githubusercontent.com/kotlinsyd/plaviohd/main/icon.png"
RAW_BASE = "https://raw.githubusercontent.com/kotlinsyd/plaviohd/main/"


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


def patch_module(module: Path, source: dict) -> None:
    name = source["name"]
    version = int(source.get("version", 1))
    icon_url = ICON_URL
    if source.get("icon"):
        icon_url = RAW_BASE + source["icon"]

    # build.gradle.kts — the source's own version, single-source
    # description, and its own logo when it has one.
    gradle = module / "build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    text = replace_regex(text, r"^version = \d+$", f"version = {version}",
                         str(gradle))
    text = replace_exact(
        text,
        'description = "PlavioHD addon inside CloudStream: movies and series '
        'with many sources, powered by the PlavioHD addon."',
        f'description = "The {name} source only, powered by the '
        f'PlavioHD addon."',
        str(gradle),
    )
    # Plugin logo: the template's iconUrl is the Plavio weave mark. Swap
    # it for the source's own logo when sources.json carries one; without
    # iconUrl, CloudStream shows a generic puzzle-piece placeholder.
    text = replace_exact(
        text,
        f'    iconUrl = "{ICON_URL}"',
        f'    iconUrl = "{icon_url}"',
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
        f"This is the **{name}** plugin, generated from the `PlavioHD` "
        f"template by `scripts/generate_source_plugins.py`.\n"
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

    # Remove previously generated modules so dropped/renamed sources
    # disappear. Generated modules are the ones carrying GENERATED.md;
    # the template itself never has one.
    for child in sorted(ROOT.iterdir()):
        if child.is_dir() and child != TEMPLATE \
                and (child / "GENERATED.md").exists():
            shutil.rmtree(child)

    for s in sources:
        if s.get("icon") and not (ROOT / s["icon"]).exists():
            raise fail(f"source {s['name']!r} names icon {s['icon']!r} "
                       f"but that file is not in the repo")

    for source in sources:
        name = source["name"]
        module = ROOT / name
        shutil.copytree(TEMPLATE, module)
        patch_module(module, source)
        print(f"generated {name}")

    print(f"{len(names)} per-source plugins generated "
          f"(plus the PlavioHD template itself, the main plugin).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
