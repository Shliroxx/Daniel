#!/usr/bin/env python3
"""Blocktexturen (128x128) fuer Maschinen, Kiste, Schmiede und die Kingdom-Erze — ersetzt die alten 16x16-Pixelbilder.

Baut auf den Material-Malern aus planet_textures.py auf (Hoehenkarte → Licht von oben links, Kanten-Glanz,
Hohlraum-Schatten). Bildschirme und Symbole werden 4-fach ueberabgetastet gezeichnet und weich verkleinert.

Ausgabe nach assets/kingdomomnitrix/textures/block/:
  arena_terminal(.png/_top)     Arena-Terminal: Gehaeuse, goldener Bildschirm mit Ranglisten-Balken, Lueftung
  weapon_terminal(.png/_top)    Gadgetron-Waffenterminal: blauer Bildschirm mit Waffen-Silhouette
  calibration_bench(.png/_top)  Omnitrix-Kalibrierbank: Anzeige mit drei Reglern, gruenes Omnitrix-Sigel oben
  bolt_crate(.png/_top)         Bolt-Kiste: Holzbohlen, Stahlbeschlaege, gelbes Mutter-Symbol
  keyblade_forge(.png/_top/_bottom)  Schluesselschwert-Schmiede: dunkler Stein, Goldkante, Krone, Leuchtrune
  mythril_ore, deepslate_mythril_ore, orichalcum_ore, raritanium_ore

Deterministisch. Aufruf aus minecraft-mod/:
    python tools/generate_machine_textures.py          # schreiben
    python tools/generate_machine_textures.py --check  # vorhanden und 128x128?
"""
from __future__ import annotations

import argparse
import logging
import math
import sys
from pathlib import Path

try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover
    sys.exit("numpy und Pillow fehlen: pip install numpy pillow")

sys.path.insert(0, str(Path(__file__).resolve().parent))
import planet_textures as tx  # noqa: E402

LOG = logging.getLogger("machine_textures")
OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "kingdomomnitrix" / "textures" / "block"
N = tx.N
SS = 4


def hc(value: str) -> np.ndarray:
    return tx.hexc(value)


def layer(draw_fn) -> np.ndarray:
    """Zeichnet mit PIL in 4-facher Groesse (Graustufen 0..255) und liefert 0..1 in N x N."""
    img = Image.new("L", (N * SS, N * SS), 0)
    draw_fn(ImageDraw.Draw(img), SS)
    return np.asarray(img.resize((N, N), Image.LANCZOS), dtype=np.float64) / 255


def rgb_layer(draw_fn) -> np.ndarray:
    img = Image.new("RGB", (N * SS, N * SS), (0, 0, 0))
    draw_fn(ImageDraw.Draw(img), SS)
    return np.asarray(img.resize((N, N), Image.LANCZOS), dtype=np.float64) / 255


def rect_mask(x0: float, y0: float, x1: float, y1: float, radius: float = 3) -> np.ndarray:
    return layer(lambda d, s: d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], radius=radius * s, fill=255))


def bevel(mask: np.ndarray, width: float) -> np.ndarray:
    """Weiche Kante: aus einer harten Maske eine abgeschraegte Hoehe."""
    img = Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(width))
    return np.asarray(img, dtype=np.float64) / 255


# --- Gehaeuse ----------------------------------------------------------------------------------------------------

def casing(name: str, dark: str, mid: str, light: str, wear: float = 1.0) -> tuple[np.ndarray, np.ndarray]:
    """Gebuerstetes Metallgehaeuse mit Rahmen, Eckschrauben, Kratzern und Schmutz in den Kanten. (Farbe, Hoehe)"""
    rng = tx.rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    d = np.minimum(np.minimum(xs, N - 1 - xs), np.minimum(ys, N - 1 - ys))
    frame = np.clip(d / 3, 0, 1) * 0.5 + np.clip((d - 9) / 2, 0, 1) * 0.5
    brushed = tx.fbm(rng, 3, 5, stretch=(6.0, 0.2))
    grime = tx.fbm(tx.rng_for(name + "g"), 4, 4)
    screws = np.zeros((N, N))
    for px in (5, N - 6):
        for py in (5, N - 6):
            r = np.hypot(xs - px, ys - py)
            screws = np.maximum(screws, np.clip(1 - r / 3.2, 0, 1))
            slot = (np.abs((xs - px) - (ys - py)) < 0.8) & (r < 2.6)
            screws = np.where(slot, screws * 0.4, screws)
    t = 0.35 + brushed * 0.3 + frame * 0.15
    color = tx.gradient_map(t, [(0, hc(dark)), (0.5, hc(mid)), (1, hc(light))])
    dirt = np.clip((1 - frame) * 0.5 + np.clip(grime - 0.6, 0, 1) * 1.5 * wear, 0, 1)
    color = color * (1 - 0.35 * dirt[..., None]) + tx.scratches(rng, int(30 * wear))[..., None] * 0.18
    height = frame * 0.7 + screws * 0.9 + brushed * 0.05
    return color, height


def vents(y0: int, y1: int, x0: int = 24, x1: int = 104, count: int = 5) -> tuple[np.ndarray, np.ndarray]:
    """Lueftungsschlitze: (Maske der Schlitze, Hoehe)."""
    def draw(d, s):
        step = (y1 - y0) / count
        for i in range(count):
            y = y0 + i * step
            d.rounded_rectangle([x0 * s, (y + 1) * s, x1 * s, (y + step - 1.5) * s], radius=1.5 * s, fill=255)
    m = layer(draw)
    return m, -bevel(m, 1.5) * 0.6


def screen(base: np.ndarray, height: np.ndarray, box, glow: str, content) -> tuple[np.ndarray, np.ndarray]:
    """Eingelassener, leuchtender Bildschirm mit Rahmen, Rasterlinien und Glanz; content zeichnet in RGB."""
    x0, y0, x1, y1 = box
    hole = rect_mask(x0, y0, x1, y1, 4)
    inner = rect_mask(x0 + 3, y0 + 3, x1 - 3, y1 - 3, 2)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    g = hc(glow)
    u = (xs - x0) / max(1, x1 - x0)
    v = (ys - y0) / max(1, y1 - y0)
    bg = g * 0.18 + g * 0.22 * (1 - v)[..., None]
    scan = (0.85 + 0.15 * (np.sin(ys * math.pi / 1.5) > 0))[..., None]
    pic = rgb_layer(content)
    lit = np.clip(bg + pic, 0, 1) * scan
    shine = np.clip(1 - (u + v * 0.6) * 2.2, 0, 1)[..., None] * 0.25
    vignette = np.clip(1 - np.hypot(u - 0.5, v - 0.5) * 1.1, 0.55, 1)[..., None]
    lit = lit * vignette + shine
    color = np.where(inner[..., None] > 0.5, lit, base * (1 - 0.55 * hole[..., None]))
    height = height - hole * 0.5 - bevel(hole, 1.5) * 0.2
    return color, height


# --- Maschinen ---------------------------------------------------------------------------------------------------

def terminal_side(name: str, glow: str, content, accent: str) -> Image.Image:
    color, height = casing(name, "2C3138", "6B7480", "A9B2BE")
    color, height = screen(color, height, (16, 14, 112, 72), glow, content)
    vm, vh = vents(82, 104, 24, 76, 4)
    color = color * (1 - 0.6 * vm[..., None])
    height = height + vh
    # Bedienknoepfe rechts unten, beleuchtet
    btn = np.zeros((N, N))
    btn_col = np.zeros((N, N, 3))
    for i, c in enumerate([accent, "39FF14", "FF4A4A"]):
        m = layer(lambda d, s, i=i: d.ellipse([(86 + i * 0) * s, (82 + i * 9) * s, (94 + i * 0) * s, (89 + i * 9) * s], fill=255))
        btn = np.maximum(btn, m)
        btn_col += m[..., None] * hc(c)
    color = np.where(btn[..., None] > 0.05, btn_col * 1.2 + 0.15 * btn[..., None], color)
    height = height + btn * 0.5
    return tx.finish(tx.compose(color, height, strength=5.0, spec=0.4, shininess=30, ambient=0.6))


def terminal_top(name: str, emblem_color: str, emblem) -> Image.Image:
    color, height = casing(name, "2C3138", "626B76", "9DA6B2")
    gm, gh = vents(20, 108, 20, 108, 9)
    em = layer(emblem)
    color = color * (1 - 0.55 * gm[..., None])
    color = np.where(em[..., None] > 0.05, hc(emblem_color) * (0.8 + 0.4 * em[..., None]), color)
    height = height + gh + em * 0.6
    return tx.finish(tx.compose(color, height, strength=5.0, spec=0.45, shininess=32, ambient=0.6))


def arena_content(d, s):
    gold = (245, 197, 66)
    for i, w in enumerate([78, 62, 50, 40]):
        y = 22 + i * 11
        d.rectangle([22 * s, y * s, (22 + w) * s, (y + 6) * s], fill=gold if i == 0 else (200, 150, 50))
        d.ellipse([(24 + w) * s, y * s, (30 + w) * s, (y + 6) * s], fill=(255, 235, 160))
    d.polygon([(96 * s, 20 * s), (100 * s, 26 * s), (106 * s, 26 * s), (101 * s, 30 * s), (103 * s, 36 * s), (96 * s, 32 * s),
               (89 * s, 36 * s), (91 * s, 30 * s), (86 * s, 26 * s), (92 * s, 26 * s)], fill=(255, 220, 90))


def weapon_content(d, s):
    blue = (110, 220, 255)
    # Waffen-Silhouette (Combuster): Lauf, Griff, Trommel
    d.rounded_rectangle([28 * s, 32 * s, 92 * s, 44 * s], radius=4 * s, fill=blue)
    d.rectangle([92 * s, 35 * s, 104 * s, 41 * s], fill=blue)
    d.polygon([(44 * s, 44 * s), (54 * s, 44 * s), (48 * s, 62 * s), (38 * s, 62 * s)], fill=blue)
    d.ellipse([58 * s, 26 * s, 76 * s, 50 * s], outline=(220, 250, 255), width=2 * s)
    for i in range(5):
        d.rectangle([(24 + i * 14) * s, 64 * s, (34 + i * 14) * s, 67 * s], fill=(80, 170, 220) if i else (255, 216, 74))


def bench_content(d, s):
    green = (57, 255, 20)
    for i, level in enumerate([0.7, 0.45, 0.85]):
        x = 30 + i * 28
        d.rounded_rectangle([x * s, 22 * s, (x + 8) * s, 64 * s], radius=3 * s, outline=(30, 120, 20), width=2 * s)
        top = 64 - level * 40
        d.rounded_rectangle([(x + 2) * s, top * s, (x + 6) * s, 62 * s], radius=2 * s, fill=green)
        d.rectangle([(x - 3) * s, (top - 2) * s, (x + 11) * s, (top + 2) * s], fill=(200, 255, 190))


def omnitrix_emblem(d, s):
    c = N / 2 * s
    d.ellipse([c - 34 * s, c - 34 * s, c + 34 * s, c + 34 * s], outline=255, width=6 * s)
    d.polygon([(c - 22 * s, c - 22 * s), (c + 22 * s, c - 22 * s), (c + 6 * s, c), (c + 22 * s, c + 22 * s), (c - 22 * s, c + 22 * s),
               (c - 6 * s, c)], fill=255)


def bolt_emblem(d, s):
    c = N / 2 * s
    pts = [(c + 30 * s * math.cos(a), c + 30 * s * math.sin(a)) for a in [i * math.pi / 3 for i in range(6)]]
    d.polygon(pts, fill=255)
    d.ellipse([c - 13 * s, c - 13 * s, c + 13 * s, c + 13 * s], fill=0)


def trophy_emblem(d, s):
    c = N / 2 * s
    d.polygon([(c - 26 * s, c - 28 * s), (c + 26 * s, c - 28 * s), (c + 18 * s, c + 2 * s), (c - 18 * s, c + 2 * s)], fill=255)
    d.rectangle([c - 5 * s, c + 2 * s, c + 5 * s, c + 18 * s], fill=255)
    d.rounded_rectangle([c - 20 * s, c + 18 * s, c + 20 * s, c + 26 * s], radius=2 * s, fill=255)


def calibration_side() -> Image.Image:
    return terminal_side("calibration_bench", "39FF14", bench_content, "39FF14")


# --- Bolt-Kiste --------------------------------------------------------------------------------------------------

def bolt_crate(name: str, emblem: bool) -> Image.Image:
    wood = tx.planks(name + "w", hc("6E3A14"), hc("B5651D"), hc("E8963A"))
    color = np.asarray(wood.convert("RGB"), dtype=np.float64) / 255
    rng = tx.rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    d = np.minimum(np.minimum(xs, N - 1 - xs), np.minimum(ys, N - 1 - ys))
    band = d < 12
    diag = (np.abs(xs - ys) < 6) | (np.abs(xs + ys - (N - 1)) < 6)
    metal = band | (diag & (d >= 12))
    brushed = tx.fbm(rng, 3, 4, stretch=(4.0, 0.3))
    steel = tx.gradient_map(0.35 + brushed * 0.4, [(0, hc("3E444C")), (0.5, hc("8E96A0")), (1, hc("D6DCE2"))])
    color = np.where(metal[..., None], steel, color)
    rivets = np.zeros((N, N))
    for p in list(range(14, N - 13, 20)):
        for q in (6, N - 7):
            for (x, y) in ((p, q), (q, p)):
                rivets = np.maximum(rivets, np.clip(1 - np.hypot(xs - x, ys - y) / 2.6, 0, 1))
    height = np.where(metal, 0.75, 0.3) + rivets * 0.5 + brushed * 0.04
    height = np.asarray(Image.fromarray((height * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), dtype=np.float64) / 255
    if emblem:
        em = layer(bolt_emblem)
        plate = rect_mask(32, 32, 96, 96, 8)
        color = np.where(plate[..., None] > 0.5, tx.gradient_map(0.3 + brushed * 0.3, [(0, hc("3A3F47")), (1, hc("6B7480"))]), color)
        color = np.where(em[..., None] > 0.1, hc("FFD84A") * (0.75 + 0.35 * em[..., None]), color)
        height = height + plate * 0.25 + em * 0.35
    color = color * (1 - 0.25 * np.clip(tx.fbm(tx.rng_for(name + "d"), 4, 4) - 0.55, 0, 1)[..., None] * 3)
    return tx.finish(tx.compose(color, height, strength=6.0, spec=0.3, shininess=26, ambient=0.6))


# --- Schluesselschwert-Schmiede --------------------------------------------------------------------------------------

def forge_stone(name: str) -> tuple[np.ndarray, np.ndarray]:
    rng = tx.rng_for(name)
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    row = (ys // 32).astype(int)
    off = np.where(row % 2 == 0, 0, 32)
    bx = (xs + off) % 64
    by = ys % 32
    dd = np.minimum(np.minimum(bx, 63 - bx), np.minimum(by, 31 - by))
    brick = np.clip(dd / 3, 0, 1)
    n = tx.fbm(rng, 8, 5)
    bid = ((xs + off) // 64 + row * 7) % 5
    tone = 0.85 + 0.06 * bid
    color = tx.gradient_map(0.25 + n * 0.5, [(0, hc("24262C")), (0.5, hc("4A4E57")), (1, hc("6E737D"))]) * tone[..., None]
    color = color * (0.55 + 0.45 * brick[..., None])
    height = brick * 0.7 + n * 0.25
    return color, height


def forge_side() -> Image.Image:
    color, height = forge_stone("keyblade_forge")
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    gold = tx.gradient_map(0.4 + tx.fbm(tx.rng_for("kfg"), 3, 3, stretch=(5, 0.3)) * 0.5, [(0, hc("8A5A0A")), (0.5, hc("F2C230")), (1, hc("FFF0A8"))])
    trim = (ys < 10) | (ys > N - 9)
    crown = layer(lambda d, s: d.polygon([(30 * s, 84 * s), (36 * s, 50 * s), (50 * s, 70 * s), (64 * s, 40 * s), (78 * s, 70 * s),
                                          (92 * s, 50 * s), (98 * s, 84 * s)], fill=255))
    color = np.where((trim | (crown > 0.5))[..., None], gold, color)
    height = height + trim * 0.8 + crown * 0.6
    return tx.finish(tx.compose(color, height, strength=6.0, spec=0.5, shininess=30, ambient=0.6))


def forge_top() -> Image.Image:
    color, height = forge_stone("keyblade_forge_top")
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    d = np.minimum(np.minimum(xs, N - 1 - xs), np.minimum(ys, N - 1 - ys))
    gold = tx.gradient_map(0.5 + tx.fbm(tx.rng_for("kft"), 3, 3) * 0.4, [(0, hc("8A5A0A")), (0.5, hc("F2C230")), (1, hc("FFF0A8"))])
    trim = d < 9
    c = N / 2 * SS

    def rune(dr, s):
        dr.ellipse([c - 40 * s, c - 40 * s, c + 40 * s, c + 40 * s], outline=255, width=3 * s)
        dr.ellipse([c - 30 * s, c - 30 * s, c + 30 * s, c + 30 * s], outline=255, width=2 * s)
        for i in range(8):
            a = i * math.pi / 4
            dr.line([(c + math.cos(a) * 30 * s, c + math.sin(a) * 30 * s), (c + math.cos(a) * 40 * s, c + math.sin(a) * 40 * s)],
                    fill=255, width=2 * s)
        # Schluesselschwert-Silhouette in der Mitte
        dr.rectangle([c - 3 * s, c - 22 * s, c + 3 * s, c + 12 * s], fill=255)
        dr.rectangle([c - 12 * s, c + 12 * s, c + 12 * s, c + 16 * s], fill=255)
        dr.rectangle([c + 3 * s, c - 22 * s, c + 13 * s, c - 16 * s], fill=255)
    r = layer(rune)
    glow = bevel(r, 3.0)
    color = np.where(trim[..., None], gold, color)
    color = color + (hc("7FE3FF") * (r * 1.0 + glow * 0.8)[..., None])
    height = height + trim * 0.8 - r * 0.3
    return tx.finish(tx.compose(color, height, strength=5.0, spec=0.45, shininess=30, ambient=0.6))


def forge_bottom() -> Image.Image:
    color, height = forge_stone("keyblade_forge_bottom")
    return tx.finish(tx.compose(color, height, strength=5.0, ambient=0.6))


# --- Erze --------------------------------------------------------------------------------------------------------

def ores() -> dict[str, Image.Image]:
    stone = tx.rock("kingdom_stone", hc("4E4E52"), hc("7A7A80"), hc("A4A4AA"), cells=12)
    deep = tx.rock("kingdom_deepslate", hc("26262C"), hc("3E3E46"), hc("5A5A64"), cells=16)
    return {
        "raritanium_ore": tx.ore("raritanium_ore", stone, hc("1E6E9E"), hc("38B8E8"), hc("C8F4FF"), clusters=5),
        "mythril_ore": tx.ore("mythril_ore", stone, hc("6888B0"), hc("A8C8E8"), hc("F4FAFF"), clusters=6, style="metal"),
        "deepslate_mythril_ore": tx.ore("deepslate_mythril_ore", deep, hc("6888B0"), hc("A8C8E8"), hc("F4FAFF"), clusters=6, style="metal"),
        "orichalcum_ore": tx.ore("orichalcum_ore", deep, hc("B07A1A"), hc("F0B84A"), hc("FFF2C0"), clusters=5, style="metal"),
    }


def build() -> dict[str, Image.Image]:
    out = {
        "arena_terminal": terminal_side("arena_terminal", "F5C542", arena_content, "F5C542"),
        "arena_terminal_top": terminal_top("arena_terminal_top", "F5C542", trophy_emblem),
        "weapon_terminal": terminal_side("weapon_terminal", "4FC3FF", weapon_content, "4FC3FF"),
        "weapon_terminal_top": terminal_top("weapon_terminal_top", "FFD84A", bolt_emblem),
        "calibration_bench": calibration_side(),
        "calibration_bench_top": terminal_top("calibration_bench_top", "39FF14", omnitrix_emblem),
        "bolt_crate": bolt_crate("bolt_crate", True),
        "bolt_crate_top": bolt_crate("bolt_crate_top", False),
        "keyblade_forge": forge_side(),
        "keyblade_forge_top": forge_top(),
        "keyblade_forge_bottom": forge_bottom(),
    }
    out.update(ores())
    return out


NAMES = ["arena_terminal", "arena_terminal_top", "weapon_terminal", "weapon_terminal_top", "calibration_bench",
         "calibration_bench_top", "bolt_crate", "bolt_crate_top", "keyblade_forge", "keyblade_forge_top", "keyblade_forge_bottom",
         "raritanium_ore", "mythril_ore", "deepslate_mythril_ore", "orichalcum_ore"]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if args.check:
        bad = []
        for n in NAMES:
            p = OUT / f"{n}.png"
            if not p.is_file():
                bad.append(f"fehlt: {n}.png")
            elif Image.open(p).size != (N, N):
                bad.append(f"{n}.png ist nicht {N}x{N}")
        for b in bad:
            LOG.error(b)
        LOG.info("Maschinen-Texturen: %d/%d in Ordnung", len(NAMES) - len(bad), len(NAMES))
        return 1 if bad else 0
    try:
        textures = build()
        missing = set(NAMES) ^ set(textures)
        if missing:
            LOG.error("Namensliste und Erzeugung passen nicht: %s", sorted(missing))
            return 1
        OUT.mkdir(parents=True, exist_ok=True)
        for name, img in textures.items():
            img.save(OUT / f"{name}.png", optimize=True)
    except OSError as exc:
        LOG.error("Schreiben fehlgeschlagen: %s", exc)
        return 1
    LOG.info("Maschinen-Texturen geschrieben: %d", len(textures))
    return 0


if __name__ == "__main__":
    sys.exit(main())
