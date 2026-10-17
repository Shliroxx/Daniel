#!/usr/bin/env python3
"""Erzeugt alle Texturen von Kingdom Omnitrix als Pixel-Art-PNGs.

Die Bilder entstehen aus ASCII-Rastern und einfachen Formen, damit sie ohne
Grafikprogramm reproduzierbar sind. Aufruf aus dem Ordner minecraft-mod/:

    python tools/generate_textures.py            # schreibt nach src/main/resources/...
    python tools/generate_textures.py --check    # prueft nur, ob alle Dateien existieren

Benoetigt Pillow (pip install pillow).
"""
from __future__ import annotations

import argparse
import logging
import random
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Hinweis fuer den Nutzer
    sys.exit("Pillow fehlt: pip install pillow")

LOG = logging.getLogger("textures")
DEFAULT_ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix"

Color = tuple[int, int, int, int]


def hexc(value: str, alpha: int = 255) -> Color:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha


def from_ascii(rows: list[str], palette: dict[str, Color], size: int = 16) -> Image.Image:
    """Baut ein Bild aus einem ASCII-Raster. '.' ist transparent, Zeilen werden aufgefuellt."""
    if len(rows) > size:
        raise ValueError(f"{len(rows)} Zeilen, erlaubt sind {size}")
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        if len(row) > size:
            raise ValueError(f"Zeile {y} hat {len(row)} Zeichen: {row!r}")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise KeyError(f"Zeichen {ch!r} fehlt in der Palette (Zeile {y})")
            img.putpixel((x, y), palette[ch])
    return img


def orb(core: str, mid: str, rim: str) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.5:
                img.putpixel((x, y), hexc(core))
            elif d < 4.5:
                img.putpixel((x, y), hexc(mid))
            elif d < 6.0:
                img.putpixel((x, y), hexc(rim, 200))
    return img


def item_textures() -> dict[str, Image.Image]:
    t: dict[str, Image.Image] = {}

    t["kingdom_key"] = from_ascii([
        "..........SSSS..",
        "..........S.SS..",
        "..........SSS.S.",
        "..........SS....",
        ".........Ss.....",
        "........Ss......",
        ".......Ss.......",
        "......Ss........",
        ".....Ss.........",
        "..Y.Ss..........",
        ".YyYs...........",
        "..YyY...........",
        ".BYYyY..........",
        "BB..Y...........",
        "B.K.............",
        ".KK.............",
    ], {"S": hexc("D6DAE3"), "s": hexc("8A8F9C"), "Y": hexc("F2C230"), "y": hexc("A87F12"),
        "B": hexc("2A3F9A"), "K": hexc("C0C0C8")})

    t["oathkeeper"] = from_ascii([
        "..........WWWW..",
        "..........W.WW..",
        "..........WWW.W.",
        "..........WW....",
        ".........Ww.....",
        "........Ww......",
        ".......Ww.......",
        "......Ww........",
        ".....Ww.........",
        "..P.Ww..........",
        ".PpPw...........",
        "..PpP...........",
        ".BPPpP..........",
        "BB..P...........",
        "B.Y.............",
        ".YYY............",
    ], {"W": hexc("F4F6FA"), "w": hexc("A9B4C8"), "P": hexc("D9DDE6"), "p": hexc("8E97A8"),
        "B": hexc("2D3A6E"), "Y": hexc("FFE14D")})

    t["heart"] = from_ascii([
        "",
        "",
        "...RRR...RRR....",
        "..RPPPR.RPPPR...",
        ".RPWPPPRPPPPPR..",
        ".RPWPPPPPPPPPR..",
        ".RPPPPPPPPPPPR..",
        ".RPPPPPPPPPPPR..",
        "..RPPPPPPPPPR...",
        "...RPPPPPPPR....",
        "....RPPPPPR.....",
        ".....RPPPR......",
        "......RPR.......",
        ".......R........",
    ], {"R": hexc("6A1B6E"), "P": hexc("D94FB8"), "W": hexc("FFD6F2")})

    t["hi_potion"] = from_ascii([
        "",
        "......CCC.......",
        "......WGW.......",
        "......WGW.......",
        ".....WGGGW......",
        "....WGGGGGW.....",
        "...WGLLLLLGW....",
        "...WLLLLLLLW....",
        "...WLlLLLLLW....",
        "...WLLLLLlLW....",
        "...WLLLLLLLW....",
        "....WLLLLLW.....",
        ".....WWWWW......",
    ], {"C": hexc("8B5A2B"), "W": hexc("DDEEFF"), "G": hexc("BFD9E8", 160),
        "L": hexc("3FCF6A"), "l": hexc("B6FFC9")})

    t["paopu_fruit"] = from_ascii([
        ".......g........",
        "......gg........",
        ".......Y........",
        "......YYY.......",
        ".....YYOYY......",
        ".YYYYYYOYYYYYY..",
        "..YYYYOOOYYYY...",
        "...YYYYOYYYY....",
        "....YYYYYYY.....",
        "....YYYYYYY.....",
        "...YYYY.YYYY....",
        "...YYY...YYY....",
        "..YY.......YY...",
    ], {"g": hexc("3E8E3E"), "Y": hexc("F7D23E"), "O": hexc("E89A1C")})

    t["omnitrix"] = from_ascii([
        "",
        ".....bbbbbb.....",
        ".....bbbbbb.....",
        "....DDDDDDDD....",
        "...DkkkkkkkkD...",
        "...DkGGGGGGkD...",
        "...DkkGGGGkkD...",
        "...DkkkGGkkkD...",
        "...DkkkGGkkkD...",
        "...DkkGGGGkkD...",
        "...DkGGGGGGkD...",
        "...DkkkkkkkkD...",
        "....DDDDDDDD....",
        ".....bbbbbb.....",
        ".....bbbbbb.....",
    ], {"b": hexc("2B2B2B"), "D": hexc("BFC5CC"), "k": hexc("111111"), "G": hexc("39FF14")})

    t["bolt"] = from_ascii([
        "",
        "",
        ".....HHHHHH.....",
        "....HhhhhhhH....",
        "...HhhHHHHhhH...",
        "..HhhH....HhhH..",
        "..HhH......HhH..",
        "..HhH......HhH..",
        "..HhH......HhH..",
        "..HhhH....HhhH..",
        "...HhhHHHHhhH...",
        "....HhhhhhhH....",
        ".....HHHHHH.....",
    ], {"H": hexc("7A6A3A"), "h": hexc("E0C060")})

    t["combuster"] = from_ascii([
        "",
        "",
        "",
        "..OOOOOOOOOOOO..",
        ".OGGGGGGGGGGGGOM",
        ".OGrrGGGGGGGGGGM",
        ".OGGGGGGGGGGGOOM",
        "..OOOOGGGOOOO...",
        ".....OGGO.......",
        ".....OGGO.......",
        "....OGGO........",
        "....OOOO........",
    ], {"O": hexc("3A3F47"), "G": hexc("8C96A3"), "r": hexc("FF7A1A"), "M": hexc("FFB347")})

    t["fusion_grenade"] = from_ascii([
        "",
        ".......kk.......",
        "......kSSk......",
        "....kkkkkkkk....",
        "...kGGGGGGGGk...",
        "..kGGWGGGGGGGk..",
        "..kGWGGGGGGGGk..",
        "..kOOOOOOOOOOk..",
        "..kOBOOOOOOBOk..",
        "..kOOOOOOOOOOk..",
        "..kGGGGGGGGGGk..",
        "...kGGGGGGGGk...",
        "....kkkkkkkk....",
    ], {"k": hexc("1E1E24"), "S": hexc("B0B0B0"), "G": hexc("7D8590"), "W": hexc("D8DEE6"),
        "O": hexc("F28C28"), "B": hexc("4FC3FF")})

    t["heli_pack"] = from_ascii([
        "",
        ".RRRRRRRRRRRRRR.",
        ".......kk.......",
        ".......kk.......",
        "....kkkkkkkk....",
        "...kSSSSSSSSk...",
        "...kSyySSyySk...",
        "...kSSSSSSSSk...",
        "...kSSSSSSSSk...",
        "...kSSGGGGSSk...",
        "...kSSSSSSSSk...",
        "....kkkkkkkk....",
    ], {"R": hexc("6E7781"), "k": hexc("2E3338"), "S": hexc("B7C1CC"), "y": hexc("7CFC00"),
        "G": hexc("5B6670")})

    t["heli_jet"] = from_ascii([
        "",
        "",
        "....kkkkkkkk....",
        "...kSSSSSSSSk...",
        "...kSyySSyySk...",
        "...kSSSSSSSSk...",
        ".kkkSSSSSSSSkkk.",
        ".kTkSSGGGGSSkTk.",
        ".kTkSSSSSSSSkTk.",
        ".kTk.kkkkkk.kTk.",
        ".kOk........kOk.",
        "..F..........F..",
        "..f..........f..",
    ], {"k": hexc("2E3338"), "S": hexc("B7C1CC"), "y": hexc("7CFC00"), "G": hexc("5B6670"),
        "T": hexc("8E99A6"), "O": hexc("F28C28"), "F": hexc("FFD84A"), "f": hexc("FF7A1A", 180)})

    t["swingshot"] = from_ascii([
        "............kk..",
        "...........kHHk.",
        "..........kHk.H.",
        ".........cHk....",
        "........c.......",
        ".......c........",
        "......c.........",
        "....kkkk........",
        "...kBBBBk.......",
        "..kBGGGBk.......",
        "..kBGGBBk.......",
        "..kkBBkk........",
        "...kRRk.........",
        "...kRRk.........",
        "....kk..........",
    ], {"k": hexc("1E1E24"), "H": hexc("D8DEE6"), "c": hexc("8E99A6"), "B": hexc("2F5DA8"),
        "G": hexc("4FC3FF"), "R": hexc("6B7580")})

    t["quest_book"] = from_ascii([
        "",
        "..kkkkkkkkkkk...",
        "..kBBBBBBBBBpk..",
        "..kBBBGGGBBBpk..",
        "..kBBGYYYGBBpk..",
        "..kBBGYKYGBBpk..",
        "..kBBGYYYGBBpk..",
        "..kBBBGGGBBBpk..",
        "..kBBBBBBBBBpk..",
        "..kBBBBBBBBBpk..",
        "..kBBBBBBBBBpk..",
        "..kBBBBBBBBBpk..",
        "..kRBBBBBBBBpk..",
        "..kkkkkkkkkkpk..",
        "...kpppppppppk..",
        "....kkkkkkkkk...",
    ], {"k": hexc("2A1A0C"), "B": hexc("7A3B1E"), "G": hexc("C9A227"), "Y": hexc("F3D36B"), "K": hexc("8A1C1C"),
        "p": hexc("EFE4C8"), "R": hexc("C0392B")})

    t["npc_spawner"] = from_ascii([
        "",
        "......kkkk......",
        ".....kSSSSk.....",
        ".....kSeSek.....",
        ".....kSSSSk.....",
        "......kkkk......",
        "....kBBBBBBk....",
        "...kBBBBBBBBk...",
        "...kBkBBBBkBk...",
        "...kSkBBBBkSk...",
        ".....kBBBBk.....",
        ".....kBkkBk.....",
        ".....kBk.kBk....",
        "...GGGGGGGGGG...",
        "..GgggggggggggG.",
        "...GGGGGGGGGG...",
    ], {"k": hexc("1E1E24"), "S": hexc("E3B98F"), "e": hexc("1E1E24"), "B": hexc("2A3F8F"),
        "G": hexc("C9A227"), "g": hexc("8A6D1A")})

    t["omega_key"] = from_ascii([
        "..........RRRR..",
        "..........R..R..",
        ".........gRRRR..",
        "..........RgR...",
        ".........Gg.....",
        "........Gg......",
        ".......Gg.......",
        "......Gg........",
        ".....Gg.........",
        "..O.Gg..........",
        "..OOg...........",
        "..kOOO..........",
        ".kGk.OO.........",
        "kGk.............",
        "kk..............",
    ], {"R": hexc("E83A3A"), "g": hexc("5B6670"), "G": hexc("A8B0B8"), "O": hexc("FF9A2E"), "k": hexc("2A2D35")})

    t["nefarious_communicator"] = from_ascii([
        "",
        "...........k....",
        "...........k....",
        "....kkkkkkkkkk..",
        "....kGGGGGGGGk..",
        "....kGBBBBBBGk..",
        "....kGBgBBgBGk..",
        "....kGBBBBBBGk..",
        "....kGBggggBGk..",
        "....kGBBBBBBGk..",
        "....kGGGGGGGGk..",
        "....kGRGGGGRGk..",
        "....kGGGGGGGGk..",
        "....kkkkkkkkkk..",
    ], {"k": hexc("1E1E24"), "G": hexc("4A4F5C"), "B": hexc("12261A"), "g": hexc("9FFF6A"), "R": hexc("E83A3A")})

    # OmniWrench: diagonaler Griff, Maulschluessel-Kopf oben rechts.
    wrench = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    handle, grip, head, head_dark = hexc("8E99A6"), hexc("2F5DA8"), hexc("C9D1DA"), hexc("6B7580")
    for i in range(10):
        x, y = 1 + i, 14 - i
        wrench.putpixel((x, y), grip if i < 4 else handle)
        wrench.putpixel((x + 1, y), head_dark)
    for y in range(0, 6):
        for x in range(10, 16):
            if x >= 13 and y <= 2:
                continue  # Maul-Oeffnung
            wrench.putpixel((x, y), head if (x + y) % 5 else head_dark)
    t["omniwrench"] = wrench

    t["dna_sample"] = from_ascii([
        "",
        "......kkkk......",
        "......kSSk......",
        ".......WW.......",
        "......WGGW......",
        ".....WGgGGW.....",
        ".....WGGgGW.....",
        ".....WGgGGW.....",
        ".....WGGgGW.....",
        ".....WGgGGW.....",
        ".....WGGgGW.....",
        "......WGGW......",
        ".......WW.......",
    ], {"k": hexc("2B2B2B"), "S": hexc("8C96A3"), "W": hexc("DDEEFF"), "G": hexc("39FF14"), "g": hexc("0E7A0E")})

    t["spell_crystal"] = from_ascii([
        "",
        ".......W........",
        "......WMC.......",
        ".....WMMCC......",
        "....WMMMCCC.....",
        "...WMMMMCCCc....",
        "...MMMMMCCCc....",
        "...MMMMMCCcc....",
        "....MMMCCcc.....",
        ".....MMCcc......",
        "......MCc.......",
        ".......c........",
    ], {"W": hexc("FFFFFF"), "M": hexc("B388FF"), "C": hexc("7C4DFF"), "c": hexc("4527A0")})

    t["fire_orb"] = orb("FFF3B0", "FF9A1F", "D9380B")
    t["ice_orb"] = orb("FFFFFF", "9BE7FF", "3A8DDB")
    t["dark_orb"] = orb("8E44AD", "2B1A40", "0A0A12")
    t["plasma_shot"] = orb("FFFFFF", "FF66E0", "8A2BE2")
    t["crystal_shard"] = from_ascii([
        "",
        "",
        ".......W........",
        "......WCC.......",
        ".....WCCCC......",
        ".....CCCCd......",
        "....WCCCCCd.....",
        "....CCCCCdd.....",
        "....CCCCCdd.....",
        ".....CCCdd......",
        ".....CCCdd......",
        "......Cdd.......",
        ".......d........",
    ], {"W": hexc("E8FFF0"), "C": hexc("5CF2A0"), "d": hexc("1E9E5E")})
    # Galvan-Erfindungen (Grey Matter): Energiezelle und Hand-Scanner
    t["galvan_cell"] = from_ascii([
        "",
        "......kkkk......",
        ".....kSSSSk.....",
        ".....kGGGGk.....",
        ".....kGWGGk.....",
        ".....kGGGGk.....",
        ".....kgGGgk.....",
        ".....kGGGGk.....",
        ".....kGWGGk.....",
        ".....kGGGGk.....",
        ".....kgggGk.....",
        ".....kSSSSk.....",
        "......kkkk......",
    ], {"k": hexc("1C1F24"), "S": hexc("9AA4B0"), "G": hexc("39FF14"), "g": hexc("1E9E1E"), "W": hexc("E8FFE0")})
    t["galvan_scanner"] = from_ascii([
        "",
        "...kkkkkkkk.....",
        "...kSSSSSSk.....",
        "...kSbbbbSk.....",
        "...kSbGGbSk.....",
        "...kSbGWbSk.....",
        "...kSbbbbSk.....",
        "...kSSSSSSkk....",
        "....kSSSSkkGk...",
        ".....kSSk..kk...",
        ".....kSSk.......",
        ".....kSSk.......",
        "......kk........",
    ], {"k": hexc("1C1F24"), "S": hexc("8C96A3"), "b": hexc("0B2A12"), "G": hexc("39FF14"), "W": hexc("E8FFE0")})
    return t


def material_textures() -> dict[str, Image.Image]:
    t: dict[str, Image.Image] = {}
    t["raritanium"] = from_ascii([
        "",
        "",
        ".......W........",
        "......WCc.......",
        ".....WCCcc......",
        "....WCCCCcc.....",
        "...WCCCCCCcc....",
        "...CCCCCCCccd...",
        "....CCCCCccd....",
        ".....CCCccd.....",
        "......Ccd.......",
        ".......d........",
    ], {"W": hexc("E8FBFF"), "C": hexc("4FD0FF"), "c": hexc("2A9AD0"), "d": hexc("17607F")})
    t["mythril_shard"] = from_ascii([
        "",
        "..........W.....",
        ".........WMm....",
        "........WMMm....",
        ".......WMMm.....",
        "......WMMm......",
        ".....WMMm.......",
        "....WMMm........",
        "...WMMm.........",
        "...MMm..........",
        "...mm...........",
    ], {"W": hexc("FFFFFF"), "M": hexc("BFD8F0"), "m": hexc("7894B4")})
    t["orichalcum"] = from_ascii([
        "",
        "",
        "....kkkkkkkk....",
        "...kYYYYYYYyk...",
        "..kYWYYYYYYyyk..",
        "..kYYYYYYYYyyk..",
        "..kYYYYYYYyyyk..",
        "...kyyyyyyyyk...",
        "....kkkkkkkk....",
    ], {"k": hexc("6B4A0E"), "Y": hexc("F5C542"), "y": hexc("C9921A"), "W": hexc("FFF6D0")})
    return t


def mod_icon(items: dict[str, Image.Image]) -> Image.Image:
    icon = Image.new("RGBA", (64, 64), hexc("14161F"))
    for (name, (x, y)) in {"kingdom_key": (0, 0), "omnitrix": (32, 0), "bolt": (0, 32), "combuster": (32, 32)}.items():
        tile = items[name].resize((32, 32), Image.NEAREST)
        icon.alpha_composite(tile, (x, y))
    return icon


def build_all(rng: random.Random) -> dict[str, Image.Image]:
    items = item_textures()
    out: dict[str, Image.Image] = {f"textures/item/{k}.png": v for k, v in items.items()}
    # Bloecke (Maschinen, Kiste, Schmiede, Erze) erzeugt tools/generate_machine_textures.py in 128x128
    out.update({f"textures/item/{k}.png": v for k, v in material_textures().items()})
    out["icon.png"] = mod_icon(items)
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="assets/kingdomomnitrix-Ordner (Standard: %(default)s)")
    parser.add_argument("--seed", type=int, default=1337, help="Zufalls-Seed fuer Holz- und Schattenrauschen")
    parser.add_argument("--check", action="store_true", help="nur pruefen, ob alle Texturen vorhanden sind")
    parser.add_argument("-v", "--verbose", action="store_true", help="jede Datei protokollieren")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s %(message)s")

    textures = build_all(random.Random(args.seed))
    if args.check:
        missing = [p for p in textures if not (args.root / p).is_file()]
        for p in missing:
            LOG.error("fehlt: %s", p)
        LOG.info("%d/%d Texturen vorhanden", len(textures) - len(missing), len(textures))
        return 1 if missing else 0

    for rel, img in textures.items():
        target = args.root / rel
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            img.save(target)
        except OSError as exc:
            LOG.error("konnte %s nicht schreiben: %s", target, exc)
            return 1
        LOG.debug("geschrieben: %s", target)
    LOG.info("%d Texturen nach %s geschrieben", len(textures), args.root)
    return 0


if __name__ == "__main__":
    sys.exit(main())
