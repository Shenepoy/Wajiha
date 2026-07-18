#!/usr/bin/env python3
"""Import Dan Patrick Recommended platform logos into composeResources.

Reads Study/…/Recommended Versions (read-only), downscales to max 512px long-edge
via ffmpeg, writes platform_logos/{variant}/{id}.png, removes platform_icons/.
"""

from __future__ import annotations

import json
import shutil
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STUDY_REC = (
    ROOT
    / "Study"
    / "v2.1_Large_(3840x2160max)_(Full_Set)_(Created_By_Dan_Patrick)"
    / "Recommended Versions (Large) (1 per platform) (Created By Dan Patrick)"
)
OUT = ROOT / "composeApp/src/commonMain/composeResources/files/platform_logos"
OLD_ICONS = ROOT / "composeApp/src/commonMain/composeResources/files/platform_icons"
PLATFORMS_DIR = ROOT / "composeApp/src/commonMain/composeResources/files/platforms"

VARIANTS = {
    "Light - Color": "light_color",
    "Dark - Color": "dark_color",
    "Light - Black & White": "light_bw",
    "Dark - Black & White": "dark_bw",
    "Light - Just White": "light_just_white",
    "Dark - Just Black": "dark_just_black",
}

MAX_EDGE = 512

# id -> list of stems to try (primary first). Alternates handle pack naming quirks.
STEMS: dict[str, list[str]] = {
    # Sony
    "psx": ["Sony Playstation"],
    "ps2": ["Sony Playstation 2"],
    "ps3": ["Sony Playstation 3"],
    "psp": ["Sony PSP"],
    "pspminis": ["Sony PSP Minis"],
    "vita": ["Sony PS Vita"],
    "pocketstation": ["Sony PocketStation"],
    # Nintendo
    "nes": ["Nintendo Entertainment System"],
    "fds": ["Nintendo Famicom Disk System"],
    "famicom": ["Nintendo Famicom"],
    "snes": ["Super Nintendo Entertainment System"],
    "sfc": ["Nintendo Super Famicom"],
    "snesmsu1": ["Super Nintendo Entertainment System"],
    "n64": ["Nintendo 64"],
    "n64dd": ["Nintendo 64DD"],
    "gc": ["Nintendo GameCube"],
    "wii": ["Nintendo Wii"],
    "wiiu": ["Nintendo Wii U"],
    "wiiware": ["Nintendo WiiWare"],
    "switch": ["Nintendo Switch"],
    "virtualboy": ["Nintendo Virtual Boy"],
    "satellaview": ["Nintendo Satellaview"],
    "sufami": ["Nintendo Sufami Turbo"],
    "sgb": ["Nintendo Super Game Boy"],
    "gb": ["Nintendo Game Boy"],
    "gbc": ["Nintendo Game Boy Color"],
    "gba": ["Nintendo Game Boy Advance"],
    "gw": ["Nintendo Game & Watch"],
    "nds": ["Nintendo DS"],
    "ndsi": ["Nintendo DSi"],
    "3ds": ["Nintendo 3DS"],
    "pokemini": ["Nintendo Pokémon Mini"],
    # Sega
    "dreamcast": ["Sega Dreamcast"],
    "genesis": ["Sega Genesis"],
    "genesismsu": ["Sega Genesis"],
    "segacd": ["Sega CD"],
    "megacd": ["Sega Mega CD", "Sega Mega-CD"],
    "sega32x": ["Sega 32X"],
    "sega32xjp": ["Sega 32X"],
    "sega32xna": ["Sega 32X"],
    "saturn": ["Sega Saturn"],
    "gamegear": ["Sega Game Gear"],
    "master": ["Sega Master System"],
    "mark3": ["Sega Mark III"],
    "sg1000": ["Sega SG-1000"],
    "model2": ["Sega Model 2"],
    "model3": ["Sega Model 3", "Sega Model 3 "],
    "naomi": ["Sega Naomi"],
    "naomi2": ["Sega Naomi 2"],
    "stv": ["Sega ST-V", "SEGA ST-V"],
    # NEC
    "tg16": ["NEC TurboGrafx-16"],
    "tgcd": ["NEC TurboGrafx-CD"],
    "pcenginecd": ["NEC PC Engine CD"],
    "supergrafx": ["NEC PC Engine SuperGrafx"],
    "pcfx": ["NEC PC-FX"],
    # SNK
    "neogeo": ["SNK Neo Geo"],
    "neogeocd": ["SNK Neo Geo CD"],
    "ngp": ["SNK Neo Geo Pocket"],
    "ngpc": ["SNK Neo Geo Pocket Color"],
    # Microsoft
    "xbox": ["Microsoft Xbox"],
    "xbox360": ["Microsoft Xbox 360"],
    "xcloud": ["Microsoft Xbox Game Pass"],
    # Atari
    "atari2600": ["Atari 2600"],
    "atari5200": ["Atari 5200"],
    "atari7800": ["Atari 7800"],
    "jaguar": ["Atari Jaguar"],
    "jaguarcd": ["Atari Jaguar CD"],
    "lynx": ["Atari Lynx"],
    "atarist": ["Atari ST"],
    # Other consoles / handhelds
    "3do": ["3DO Interactive Multiplayer"],
    "cdi": ["Philips CD-i"],
    "coleco": ["ColecoVision"],
    "channelf": ["Fairchild Channel F"],
    "odyssey2": ["Magnavox Odyssey 2"],
    "intellivision": ["Mattel Intellivision"],
    "vectrex": ["GCE Vectrex"],
    "arcadia": ["Emerson Arcadia 2001"],
    "astrocde": ["Bally Astrocade"],
    "megaduck": ["Mega Duck"],
    "ngage": ["Nokia N-Gage"],
    "supervision": ["Watara Supervision"],
    "ws": ["WonderSwan"],
    "wsc": ["Wonderswan Color"],
    "g7400": ["Phillips Videopac+"],
    "videopac": ["Phillips Videopac+"],
    "gx4000": ["Amstrad GX4000"],
    "amigacd32": ["Commodore Amiga CD32"],
    "cdtv": ["Commodore CDTV"],
    "pv1000": ["Casio PV-1000"],
    "scv": ["Epoch Super Cassette Vision"],
    "supracan": ["Funtech Super Acan"],
    "gamate": ["Gamate"],
    "gmaster": ["Hartung Game Master"],
    "gamecom": ["Tiger Game.com"],
    "pico8": ["Pico-8"],
    "openbor": ["OpenBOR", "OpenBOR-01"],
    "fmtowns": ["Fujitsu FM Towns Marty"],
    # Arcade
    "mame": ["MAME"],
    "fbneo": ["Final Burn Neo"],
    "fba": ["Final Burn Alpha"],
    "arcade": ["Arcade Classics"],
    "cps1": ["Capcom Play System"],
    "cps2": ["Capcom Play System II"],
    "cps3": ["Capcom Play System III"],
    "cps": ["Capcom Play System"],
    "atomiswave": ["Sammy Atomiswave"],
    "daphne": ["Daphne"],
    "vpinball": ["Pinball"],
    # Computers / PC
    "dos": ["MS-DOS"],
    "pc": ["Windows"],
    "windows": ["Windows"],
    "windows3x": ["Windows 3.x"],
    "windows9x": ["Windows"],
    "amiga": ["Commodore Amiga"],
    "amiga600": ["Commodore Amiga"],
    "amiga1200": ["Commodore Amiga 1200"],
    "c64": ["Commodore 64"],
    "cpc": ["Amstrad CPC"],
    "amstradcpc": ["Amstrad CPC"],
    "msx": ["Microsoft MSX"],
    "msx1": ["Microsoft MSX"],
    "msx2": ["Microsoft MSX2"],
    "msxturbor": ["Microsoft MSX Turbo R"],
    "appleii": ["Apple II"],
    "apple2": ["Apple II"],
    "macintosh": ["Apple Mac OS"],
    "zxspectrum": ["Sinclair ZX Spectrum"],
    "scummvm": ["ScummVM"],
    "bbcmicro": ["BBC Microcomputer System"],
    "x68000": ["Sharp X68000", "Sharp X6800"],
    "tic80": ["TIC-80"],
    "pet": ["Commodore PET"],
    "plus4": ["Commodore Plus 4"],
    "vic20": ["Commodore VIC-20"],
    "android": ["Android"],
    "ios": ["Apple iOS"],
}

# Lookup aliases → canonical file id (must be a key in STEMS or another alias target).
ALIASES: dict[str, str] = {
    "psvita": "vita",
    "ps1": "psx",
    "ps": "psx",
    "playstation": "psx",
    "playstation2": "ps2",
    "playstation3": "ps3",
    "megadrive": "genesis",
    "md": "genesis",
    "gen": "genesis",
    "msu-md": "genesismsu",
    "mastersystem": "master",
    "sms": "master",
    "sg-1000": "sg1000",
    "gameandwatch": "gw",
    "pcengine": "tg16",
    "pce": "tg16",
    "tgfx": "tg16",
    "tg-cd": "tgcd",
    "pcecd": "tgcd",
    "pcenginecd": "tgcd",
    "sgfx": "supergrafx",
    "wonderswan": "ws",
    "wonderswancolor": "wsc",
    "atarijaguar": "jaguar",
    "atarilynx": "lynx",
    "cdimono1": "cdi",
    "colecovision": "coleco",
    "n3ds": "3ds",
    "nintendo3ds": "3ds",
    "nintendoswitch": "switch",
    "nsw": "switch",
    "gamecube": "gc",
    "ngc": "gc",
    "bsx": "satellaview",
    "ss": "saturn",
    "dc": "dreamcast",
    "gg": "gamegear",
    "fbneo": "fbneo",
    "mame": "mame",
    "neogeoaes": "neogeo",
    "ng": "neogeo",
    "32x": "sega32x",
    "megacd": "megacd",
}


def png_dims(path: Path) -> tuple[int, int]:
    with path.open("rb") as f:
        assert f.read(8).startswith(b"\x89PNG")
        length, typ = struct.unpack(">I4s", f.read(8))
        assert typ == b"IHDR"
        w, h = struct.unpack(">II", f.read(8))
        return w, h


def index_variant(variant_dir: Path) -> dict[str, Path]:
    """stem -> path (exact stem as on disk)."""
    out: dict[str, Path] = {}
    for p in variant_dir.rglob("*.png"):
        out[p.stem] = p
    return out


def resolve(index: dict[str, Path], stems: list[str]) -> Path | None:
    for stem in stems:
        if stem in index:
            return index[stem]
        # trailing-space / case-insensitive fallback
        for k, p in index.items():
            if k.rstrip() == stem.rstrip():
                return p
            if k.lower() == stem.lower():
                return p
    return None


def resize_ffmpeg(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        str(src),
        "-vf",
        f"scale={MAX_EDGE}:{MAX_EDGE}:force_original_aspect_ratio=decrease",
        "-frames:v",
        "1",
        str(dst),
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def bundled_ids() -> set[str]:
    ids: set[str] = set()
    daij = PLATFORMS_DIR / "daijishou"
    for p in daij.glob("*.json"):
        data = json.loads(p.read_text())
        plat = data.get("platform") or data
        uid = plat.get("uniqueId")
        if uid:
            ids.add(uid)
    iisu = json.loads((PLATFORMS_DIR / "iisu_consoles.json").read_text())
    for c in iisu.get("consoles", []):
        sn = c.get("shortName")
        if sn:
            ids.add(sn)
    return ids


def main() -> int:
    if not STUDY_REC.is_dir():
        print(f"ERROR: Recommended pack not found: {STUDY_REC}", file=sys.stderr)
        return 1
    if shutil.which("ffmpeg") is None:
        print("ERROR: ffmpeg not found", file=sys.stderr)
        return 1

    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    indexes = {folder: index_variant(STUDY_REC / folder) for folder in VARIANTS}

    zero: list[str] = []
    partial: list[tuple[str, int]] = []
    ok_ids: list[str] = []

    for pid, stems in sorted(STEMS.items()):
        found = 0
        for folder, key in VARIANTS.items():
            src = resolve(indexes[folder], stems)
            if src is None:
                continue
            dst = OUT / key / f"{pid}.png"
            resize_ffmpeg(src, dst)
            found += 1
        if found == 0:
            zero.append(pid)
        elif found < len(VARIANTS):
            partial.append((pid, found))
            ok_ids.append(pid)
        else:
            ok_ids.append(pid)

    if zero:
        print("ERROR: no logo in any variant for:", ", ".join(zero), file=sys.stderr)
        return 1

    if OLD_ICONS.exists():
        shutil.rmtree(OLD_ICONS)
        print(f"Removed {OLD_ICONS.relative_to(ROOT)}")

    bundled = bundled_ids()
    mapped_set = set(STEMS) | set(ALIASES)
    # ids that resolve via STEMS or alias target in STEMS
    covered = {i for i in bundled if i in STEMS or ALIASES.get(i) in STEMS}
    skipped = sorted(bundled - covered)

    # size check
    over: list[str] = []
    for p in OUT.rglob("*.png"):
        w, h = png_dims(p)
        if max(w, h) > MAX_EDGE:
            over.append(f"{p.relative_to(OUT)} {w}x{h}")

    total_files = len(list(OUT.rglob("*.png")))
    total_mb = sum(p.stat().st_size for p in OUT.rglob("*.png")) / (1024 * 1024)

    print("=== Dan Patrick logo import ===")
    print(f"Mapped ids with assets: {len(ok_ids)}")
    print(f"Partial variants: {len(partial)}")
    for pid, n in partial:
        print(f"  warn {pid}: {n}/{len(VARIANTS)} variants")
    print(f"Output files: {total_files} ({total_mb:.1f} MB)")
    print(f"Bundled platform ids: {len(bundled)}")
    print(f"Bundled covered (id or alias): {len(covered)}")
    print(f"Bundled skipped (no logo): {len(skipped)}")
    for s in skipped:
        print(f"  skip {s}")
    if over:
        print("ERROR: oversized:", over, file=sys.stderr)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
