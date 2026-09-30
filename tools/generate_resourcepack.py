#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the "role cards" resource pack of No Game No Life UHC.

What it produces
  * resourcepack/                        the unzipped pack (sources, committed in git)
  * src/main/resources/resourcepack/NGNL-ResourcePack.zip   the pack bundled in the plugin jar
  * src/main/resources/cards/glyphs.yml  the glyph layout read by the plugin (RoleCardManager)

How the cards are shown in game
  The plugin opens a chest inventory whose TITLE is made of custom font glyphs.  Every glyph is one
  tile of the card picture (the card is cut in GRID x GRID tiles, 2 x 2 tiles of 256 px = 512 px
  by default).  Tiles are drawn by the bitmap font assets/ngnl/font/card.json, so no shader or mod
  is needed.  Each role has two pages: the front (portrait + objective) and the back (powers).

Customising
  * Portraits: drop a PNG named after the role (tools/portraits/SORA.png, SHIRO.png, ...) and run
    this script again: it replaces the generated placeholder portrait.
  * Texts: edit tools/cards_data.py.
  * Bigger cards: --size 1024 (tiles of 512 px) or --grid 4 (16 tiles of 128 px).

Usage:  python3 tools/generate_resourcepack.py [--size 512] [--grid 2] [--font path.ttf]
Requires Pillow (pip install pillow).
"""
import argparse
import colorsys
import hashlib
import json
import os
import shutil
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cards_data import FACTIONS, ROLES  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_DIR = os.path.join(ROOT, "resourcepack")
PORTRAIT_DIR = os.path.join(ROOT, "tools", "portraits")
JAR_RESOURCES = os.path.join(ROOT, "src", "main", "resources")

# Private-use code points used by the plugin title (see RoleCardManager)
LEFT_SHIFT_CHAR = 0xE000
JOIN_CHAR = 0xE001
BACK_CHAR = 0xE002
TILE_CHAR_BASE = 0xE100

DISPLAY_TILE = 100      # size in GUI pixels of one tile
BASELINE = 13           # y of the baseline of a container title (top 6 + font ascent 7)
GUI_WIDTH, GUI_HEIGHT = 176, 222   # 6-row chest GUI

GOLD = (231, 198, 107)
WHITE = (255, 255, 255)

FONT_CANDIDATES_BOLD = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
]
FONT_CANDIDATES_REGULAR = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
    "/Library/Fonts/Arial.ttf",
    "C:/Windows/Fonts/arial.ttf",
]


# ---------------------------------------------------------------------------------------- fonts
def find_font(custom, candidates):
    """Return the path of the first existing font file (custom path first)."""
    for path in ([custom] if custom else []) + candidates:
        if path and os.path.exists(path):
            return path
    return None


class Fonts:
    """Loads fonts at any size and caches them."""

    def __init__(self, custom):
        self.bold = find_font(custom, FONT_CANDIDATES_BOLD)
        self.regular = find_font(custom, FONT_CANDIDATES_REGULAR)
        self.cache = {}

    def get(self, size, bold=False):
        key = (size, bold)
        if key not in self.cache:
            path = self.bold if bold else self.regular
            self.cache[key] = ImageFont.truetype(path, size) if path else ImageFont.load_default(size)
        return self.cache[key]


# ---------------------------------------------------------------------------------------- colours
def hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def shade(color, factor):
    """Multiply a colour by a factor (<1 darker, >1 lighter)."""
    return tuple(max(0, min(255, int(c * factor))) for c in color)


def accent_for(key):
    """A stable accent colour per role (used by the placeholder portrait)."""
    digest = hashlib.sha1(key.encode()).digest()
    hue = digest[0] / 255.0
    r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 0.95)
    return int(r * 255), int(g * 255), int(b * 255)


# ---------------------------------------------------------------------------------------- drawing
def vertical_gradient(size, top, bottom):
    """Image filled with a vertical gradient."""
    image = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(image)
    for y in range(size):
        t = y / (size - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line([(0, y), (size, y)], fill=color)
    return image


def draw_text(draw, position, text, font, fill=WHITE, anchor="la", outline=2):
    """Draw text with a dark outline so it stays readable on any background."""
    x, y = position
    for dx in range(-outline, outline + 1):
        for dy in range(-outline, outline + 1):
            if dx or dy:
                draw.text((x + dx, y + dy), text, font=font, fill=(10, 10, 20), anchor=anchor)
    draw.text((x, y), text, font=font, fill=fill, anchor=anchor)


def wrap(text, font, max_width):
    """Split a text in lines that fit a pixel width."""
    lines, current = [], ""
    for word in text.split():
        candidate = (current + " " + word).strip()
        if font.getlength(candidate) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def frame(image, faction_color):
    """Draw the card border."""
    size = image.size[0]
    draw = ImageDraw.Draw(image)
    scale = size / 512
    draw.rectangle([0, 0, size - 1, size - 1], outline=faction_color, width=max(2, int(9 * scale)))
    inset = int(14 * scale)
    draw.rectangle([inset, inset, size - 1 - inset, size - 1 - inset], outline=GOLD, width=max(1, int(2 * scale)))
    return draw


def background(size, faction_color):
    """Dark gradient tinted with the faction colour, with a soft vignette."""
    image = vertical_gradient(size, shade(faction_color, 0.42), shade(faction_color, 0.12))
    vignette = Image.new("L", (size, size), 0)
    ImageDraw.Draw(vignette).ellipse([-size * 0.2, -size * 0.2, size * 1.2, size * 1.2], fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(size / 6))
    dark = Image.new("RGB", (size, size), (0, 0, 0))
    return Image.composite(image, dark, vignette.point(lambda v: 130 + v * 125 // 255))


# ---------------------------------------------------------------------------------------- portrait
def placeholder_portrait(role, faction_color, width, height):
    """Stylised placeholder: gradient, big initial, silhouette with glowing eyes."""
    accent = accent_for(role["key"])
    image = vertical_gradient(max(width, height), shade(faction_color, 0.9), shade(faction_color, 0.25))
    image = image.resize((width, height))
    draw = ImageDraw.Draw(image, "RGBA")

    for i in range(-height, width, 46):   # diagonal light streaks
        draw.polygon([(i, height), (i + 18, height), (i + 18 + height // 2, 0), (i + height // 2, 0)], fill=(255, 255, 255, 14))

    # The big initial is drawn on its own layer: ImageDraw ignores the alpha of text on RGB images.
    font_path = find_font(None, FONT_CANDIDATES_BOLD)
    big = ImageFont.truetype(font_path, int(height * 0.95)) if font_path else ImageFont.load_default(int(height * 0.95))
    layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((width // 2, height // 2 + 10), role["name"][0].upper(), font=big,
                               fill=(255, 255, 255, 26), anchor="mm")
    image = Image.alpha_composite(image.convert("RGBA"), layer)
    draw = ImageDraw.Draw(image, "RGBA")

    cx = width // 2
    body = shade(faction_color, 0.35)
    draw.ellipse([cx - 110, height - 70, cx + 110, height + 120], fill=body + (255,))
    draw.ellipse([cx - 44, height // 2 - 52, cx + 44, height // 2 + 40], fill=shade(faction_color, 0.28) + (255,))
    draw.ellipse([cx - 52, height // 2 - 70, cx + 52, height // 2 - 10], fill=shade(accent, 0.45) + (255,))   # hair
    for ex in (-17, 17):
        draw.ellipse([cx + ex - 7, height // 2 - 14, cx + ex + 7, height // 2 + 2], fill=accent + (255,))
        draw.ellipse([cx + ex - 14, height // 2 - 21, cx + ex + 14, height // 2 + 9], fill=accent + (40,))
    return image.convert("RGB")


def load_portrait(role, faction_color, width, height):
    """Use tools/portraits/<KEY>.png when present (cover-fit), else the placeholder."""
    path = os.path.join(PORTRAIT_DIR, role["key"] + ".png")
    if not os.path.exists(path):
        return placeholder_portrait(role, faction_color, width, height)
    art = Image.open(path).convert("RGB")
    scale = max(width / art.width, height / art.height)
    art = art.resize((int(art.width * scale) + 1, int(art.height * scale) + 1), Image.LANCZOS)
    left, top = (art.width - width) // 2, (art.height - height) // 2
    return art.crop((left, top, left + width, top + height))


# ---------------------------------------------------------------------------------------- pages
def draw_front(role, fonts, size):
    """Front of the card: name, faction, portrait, duo partner and objective."""
    scale = size / 512
    faction_color = hex_to_rgb(FACTIONS[role["faction"]])
    image = background(size, faction_color)
    draw = frame(image, faction_color)
    s = lambda v: int(v * scale)   # noqa: E731

    title_font = fonts.get(s(40), True)
    while title_font.getlength(role["name"]) > size - s(80) and title_font.size > s(20):
        title_font = fonts.get(title_font.size - 2, True)
    draw_text(draw, (size // 2, s(50)), role["name"], title_font, anchor="mm", outline=s(3))

    chip_font = fonts.get(s(20), True)
    chip_text = role["faction"].upper()
    chip_width = int(chip_font.getlength(chip_text)) + s(36)
    chip = [size // 2 - chip_width // 2, s(84), size // 2 + chip_width // 2, s(110)]
    draw.rounded_rectangle(chip, radius=s(13), fill=faction_color, outline=GOLD, width=max(1, s(2)))
    draw.text((size // 2, s(97)), chip_text, font=chip_font, fill=WHITE, anchor="mm")

    box = [s(34), s(122), size - s(34), s(318)]
    portrait = load_portrait(role, faction_color, box[2] - box[0], box[3] - box[1])
    image.paste(portrait, (box[0], box[1]))
    draw.rectangle(box, outline=GOLD, width=max(2, s(3)))

    y = s(334)
    if role.get("duo"):
        draw_text(draw, (size // 2, y), "Duo avec " + role["duo"], fonts.get(s(21), True), fill=GOLD, anchor="ma", outline=s(2))
        y += s(30)
    draw_text(draw, (s(40), y), "OBJECTIF", fonts.get(s(19), True), fill=GOLD, anchor="la", outline=s(2))
    y += s(26)
    body_font = fonts.get(s(22))
    for line in wrap(role["objective"], body_font, size - s(80))[:3]:
        draw_text(draw, (s(40), y), line, body_font, anchor="la", outline=s(2))
        y += s(26)

    hint_font = fonts.get(s(17))
    draw_text(draw, (size // 2, size - s(34)), "Retourne la carte pour voir tes pouvoirs  >", hint_font,
              fill=(210, 210, 230), anchor="mm", outline=s(2))
    return image


def layout_sections(sections, fonts, size, top, bottom):
    """Find the biggest font size for which all the sections fit between top and bottom.

    Returns (font_size, [(kind, text)]) where kind is 'head' or 'line'.
    """
    scale = size / 512
    width = size - int(84 * scale)
    for font_size in range(int(22 * scale), int(12 * scale) - 1, -1):
        body = fonts.get(font_size)
        head = fonts.get(font_size + int(2 * scale), True)
        rows, y = [], top
        line_height = int(font_size * 1.22)
        for title, bullets in sections:
            rows.append(("head", title))
            y += int(line_height * 1.35)
            for bullet in bullets:
                for index, line in enumerate(wrap(bullet, body, width - int(18 * scale))):
                    rows.append(("line" if index else "bullet", line))
                    y += line_height
                y += int(line_height * 0.18)
            y += int(line_height * 0.45)
        if y <= bottom:
            return font_size, rows
    return int(12 * scale), rows


def draw_back(role, fonts, size):
    """Back of the card: powers during the mining phase and in the finale."""
    scale = size / 512
    s = lambda v: int(v * scale)   # noqa: E731
    faction_color = hex_to_rgb(FACTIONS[role["faction"]])
    image = background(size, faction_color)
    draw = frame(image, faction_color)

    draw_text(draw, (size // 2, s(46)), "Pouvoirs de " + role["name"], fonts.get(s(28), True), anchor="mm", outline=s(3))
    draw.line([(s(40), s(72)), (size - s(40), s(72))], fill=GOLD, width=max(1, s(2)))

    sections = [("PHASE DE MINAGE", role["mining"]), ("FINALE - LOVE FIGHT", role["finale"])]
    font_size, rows = layout_sections(sections, fonts, size, s(86), size - s(40))
    body = fonts.get(font_size)
    head = fonts.get(font_size + s(2), True)
    line_height = int(font_size * 1.22)

    y = s(86)
    for kind, text in rows:
        if kind == "head":
            draw_text(draw, (s(40), y), text, head, fill=GOLD, outline=s(2))
            y += int(line_height * 1.35)
        else:
            x = s(40) if kind == "bullet" else s(58)
            if kind == "bullet":
                draw_text(draw, (s(40), y), "-", body, fill=faction_color if sum(faction_color) > 200 else WHITE, outline=s(2))
            draw_text(draw, (s(58), y), text, body, outline=s(2))
            y += line_height
            if kind == "bullet" or kind == "line":
                pass
    return image


# ---------------------------------------------------------------------------------------- output
def save_tiles(image, key, page, grid, tile_dir):
    """Cut a card in grid x grid tiles and save them; returns the tile names (row-major)."""
    tile = image.size[0] // grid
    names = []
    for row in range(grid):
        for column in range(grid):
            name = "%s_%d_%d%d" % (key.lower(), page, row, column)
            crop = image.crop((column * tile, row * tile, (column + 1) * tile, (row + 1) * tile))
            crop.save(os.path.join(tile_dir, name + ".png"), optimize=True)
            names.append(name)
    return names


def write_font_json(font_path, entries, grid):
    """Write assets/ngnl/font/card.json (space provider + one bitmap provider per tile)."""
    advance = int(0.5 + DISPLAY_TILE) + 1
    back = -(advance * grid - (grid - 1))
    left = int((GUI_WIDTH - DISPLAY_TILE * grid) / 2) - 8
    providers = [{"type": "space", "advances": {
        chr(LEFT_SHIFT_CHAR): left, chr(JOIN_CHAR): -1, chr(BACK_CHAR): back}}]

    top_ascent = BASELINE - int((GUI_HEIGHT - DISPLAY_TILE * grid) / 2)
    for code, name, row in entries:
        providers.append({"type": "bitmap", "file": "ngnl:font/cards/%s.png" % name,
                          "ascent": top_ascent - row * DISPLAY_TILE, "height": DISPLAY_TILE,
                          "chars": [chr(code)]})
    with open(font_path, "w", encoding="utf-8") as handle:
        json.dump({"providers": providers}, handle, ensure_ascii=True, indent=1)
    return left, back


def write_glyph_layout(path, grid, left, back, layout):
    """Write the YAML file the plugin reads to build the inventory titles."""
    lines = ["# Generated by tools/generate_resourcepack.py - do not edit by hand",
             "grid: %d" % grid,
             "left-shift-char: %d   # %d px" % (LEFT_SHIFT_CHAR, left),
             "join-char: %d   # -1 px" % JOIN_CHAR,
             "back-char: %d   # %d px" % (BACK_CHAR, back),
             "roles:"]
    for key, pages in layout.items():
        lines.append("  %s:" % key)
        lines.append("    front: [%s]" % ", ".join(str(c) for c in pages[0]))
        lines.append("    back: [%s]" % ", ".join(str(c) for c in pages[1]))
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def write_pack_meta(pack_dir):
    """pack.mcmeta (pack format 46 = Minecraft 1.21.4) and the pack icon."""
    meta = {"pack": {"pack_format": 46, "description": "§5No Game No Life UHC §7- cartes de rôles",
                     "supported_formats": {"min_inclusive": 34, "max_inclusive": 99}}}
    with open(os.path.join(pack_dir, "pack.mcmeta"), "w", encoding="utf-8") as handle:
        json.dump(meta, handle, ensure_ascii=False, indent=2)
    icon = background(64, (138, 79, 214))
    draw = ImageDraw.Draw(icon)
    draw.rectangle([0, 0, 63, 63], outline=GOLD, width=2)
    draw.text((32, 32), "NGNL", font=ImageFont.load_default(18), fill=WHITE, anchor="mm")
    icon.save(os.path.join(pack_dir, "pack.png"))


def zip_pack(pack_dir, zip_path):
    """Zip the pack deterministically (fixed timestamps, sorted entries)."""
    os.makedirs(os.path.dirname(zip_path), exist_ok=True)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for folder, _, files in sorted(os.walk(pack_dir)):
            for filename in sorted(files):
                full = os.path.join(folder, filename)
                info = zipfile.ZipInfo(os.path.relpath(full, pack_dir).replace(os.sep, "/"), (2024, 1, 1, 0, 0, 0))
                info.external_attr = 0o644 << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(full, "rb") as handle:
                    archive.writestr(info, handle.read())
    with open(zip_path, "rb") as handle:
        return hashlib.sha1(handle.read()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--size", type=int, default=512, help="size in pixels of a whole card (default 512)")
    parser.add_argument("--grid", type=int, default=2, help="the card is cut in grid x grid tiles (default 2)")
    parser.add_argument("--font", default=None, help="path of a TTF font to use")
    args = parser.parse_args()
    if args.size % args.grid:
        sys.exit("--size must be a multiple of --grid")

    fonts = Fonts(args.font)
    shutil.rmtree(PACK_DIR, ignore_errors=True)
    tile_dir = os.path.join(PACK_DIR, "assets", "ngnl", "textures", "font", "cards")
    font_dir = os.path.join(PACK_DIR, "assets", "ngnl", "font")
    os.makedirs(tile_dir)
    os.makedirs(font_dir)

    entries, layout = [], {}
    for index, role in enumerate(ROLES):
        pages = []
        for page, draw_page in enumerate((draw_front, draw_back)):
            image = draw_page(role, fonts, args.size)
            names = save_tiles(image, role["key"], page, args.grid, tile_dir)
            base = TILE_CHAR_BASE + (index * 2 + page) * args.grid * args.grid
            codes = [base + i for i in range(len(names))]
            pages.append(codes)
            entries.extend((code, name, i // args.grid) for i, (code, name) in enumerate(zip(codes, names)))
        layout[role["key"]] = pages

    left, back = write_font_json(os.path.join(font_dir, "card.json"), entries, args.grid)
    write_pack_meta(PACK_DIR)

    os.makedirs(os.path.join(JAR_RESOURCES, "cards"), exist_ok=True)
    write_glyph_layout(os.path.join(JAR_RESOURCES, "cards", "glyphs.yml"), args.grid, left, back, layout)
    digest = zip_pack(PACK_DIR, os.path.join(JAR_RESOURCES, "resourcepack", "NGNL-ResourcePack.zip"))
    print("Generated %d roles, %d tiles. Pack SHA-1: %s" % (len(ROLES), len(entries), digest))


if __name__ == "__main__":
    main()
