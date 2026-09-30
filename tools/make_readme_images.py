#!/usr/bin/env python3
"""Build the pictures used by README.md from the generated card tiles (run generate_resourcepack.py first)."""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from cards_data import ROLES  # noqa: E402

TILES = os.path.join(ROOT, "resourcepack", "assets", "ngnl", "textures", "font", "cards")
OUT = os.path.join(ROOT, "docs", "images")


def card(key, page):
    """Reassemble a card from its 2x2 tiles."""
    image = Image.new("RGB", (512, 512))
    for row in range(2):
        for col in range(2):
            image.paste(Image.open(os.path.join(TILES, "%s_%d_%d%d.png" % (key.lower(), page, row, col))), (col * 256, row * 256))
    return image


def gallery():
    """Front of every role in one picture."""
    cols, size = 6, 200
    rows = (len(ROLES) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * size, rows * size), (18, 18, 28))
    for i, role in enumerate(ROLES):
        sheet.paste(card(role["key"], 0).resize((size - 8, size - 8), Image.LANCZOS), ((i % cols) * size + 4, (i // cols) * size + 4))
    sheet.save(os.path.join(OUT, "cards-gallery.png"), optimize=True)


def example(key):
    """Front and back of one role side by side."""
    sheet = Image.new("RGB", (512 * 2 + 30, 512 + 20), (18, 18, 28))
    sheet.paste(card(key, 0), (10, 10))
    sheet.paste(card(key, 1), (512 + 20, 10))
    sheet.save(os.path.join(OUT, "card-example.png"), optimize=True)


def mockup(key):
    """Approximate look of the popup in game: a 6-row chest GUI with the card drawn over it (GUI scale 3)."""
    scale = 3
    gui = Image.new("RGB", (176 * scale, 222 * scale), (198, 198, 198))
    draw = ImageDraw.Draw(gui)
    draw.rectangle([0, 0, gui.width - 1, gui.height - 1], outline=(55, 55, 55), width=scale * 2)
    for row in range(6):
        for col in range(9):
            x, y = (7 + col * 18) * scale, (17 + row * 18) * scale
            draw.rectangle([x, y, x + 16 * scale, y + 16 * scale], fill=(139, 139, 139))
    shown = card(key, 0).resize((200 * scale, 200 * scale), Image.LANCZOS)
    canvas = Image.new("RGB", (shown.width + 40, gui.height + 40), (30, 30, 38))
    gui_x, gui_y = (canvas.width - gui.width) // 2, 20
    canvas.paste(gui, (gui_x, gui_y))
    canvas.paste(shown, (gui_x + (gui.width - shown.width) // 2, gui_y + 11 * scale))
    font = ImageFont.load_default(18)
    for slot, label, color in ((0, "X", (200, 60, 60)), (8, ">", (230, 200, 90))):
        x = gui_x + (7 + (slot % 9) * 18) * scale
        y = gui_y + (17 + (slot // 9) * 18) * scale
        draw = ImageDraw.Draw(canvas)
        draw.rectangle([x, y, x + 16 * scale, y + 16 * scale], fill=color, outline=(20, 20, 20), width=2)
        draw.text((x + 8 * scale, y + 8 * scale), label, font=font, fill=(20, 20, 20), anchor="mm")
    canvas.save(os.path.join(OUT, "popup-mockup.png"), optimize=True)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    gallery()
    example("SORA")
    mockup("SORA")
    print("Images written to", OUT)
