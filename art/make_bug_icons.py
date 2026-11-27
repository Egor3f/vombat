"""Regenerates notification icons from bug8.svg. Needs rsvg-convert, Pillow and numpy.

Small icon: Android uses only the alpha channel, so it is a silhouette of the bug without texture layers,
with the outlines inside the silhouette cut out and thickened to stay visible at 24dp.
"""
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ART = Path(__file__).resolve().parent
RES = ART.parent / "app/src/main/res"
RENDER_PX = 1024
SMALL_DP = 24
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LARGE_PX = 256
LABEL = "{http://www.inkscape.org/namespaces/inkscape}label"
TEXTURE_LAYERS = {
    "Пятна", "Пятна Н", "Пятна С", "Пятна Т", "Тень", "Тень 1", "Тень 2", "Глубокая тень", "Акцент",
    "Фактура", "Затенение", "Блики", "Линии", "Лучи", "Тень лучей", "Подсветка", "Бровь",
}
OUTLINE_RGB = np.array([0x39, 0x24, 0x04]) / 255
OUTLINE_TOLERANCE = 0.15
CUT_WIDTH_PX = 9
EDGE_KEEP_PX = 25


def render(svg: Path, out: Path) -> Image.Image:
    subprocess.run(["rsvg-convert", "-h", str(RENDER_PX), str(svg), "-o", str(out)], check=True)
    image = Image.open(out).convert("RGBA")
    return image.crop(image.getbbox())


def square(image: Image.Image) -> Image.Image:
    side = max(image.size)
    canvas = Image.new(image.mode, (side, side))
    canvas.paste(image, ((side - image.width) // 2, (side - image.height) // 2))
    return canvas


def silhouette(image: Image.Image) -> Image.Image:
    pixels = np.asarray(image).astype(float) / 255
    solid = pixels[..., 3] > 0.5
    outline = np.linalg.norm(pixels[..., :3] - OUTLINE_RGB, axis=-1) < OUTLINE_TOLERANCE
    cut = Image.fromarray((outline & solid).astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(CUT_WIDTH_PX))
    inner = Image.fromarray(solid.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(EDGE_KEEP_PX))
    mask = solid & ~(np.asarray(cut) > 0) | solid & ~(np.asarray(inner) > 0)
    return Image.fromarray(mask.astype(np.uint8) * 255)


def main() -> None:
    ET.register_namespace("", "http://www.w3.org/2000/svg")
    tree = ET.parse(ART / "bug8.svg")
    for element in tree.iter():
        if element.get(LABEL) in TEXTURE_LAYERS:
            element.set("style", (element.get("style") or "") + ";display:none")
    with tempfile.TemporaryDirectory() as tmp:
        simple_svg = Path(tmp) / "simple.svg"
        tree.write(simple_svg)
        color = square(render(ART / "bug8.svg", Path(tmp) / "bug.png"))
        mask = square(silhouette(render(simple_svg, Path(tmp) / "simple.png")))

    for density, scale in DENSITIES.items():
        px = round(SMALL_DP * scale)
        icon = Image.new("RGBA", (px, px), (255, 255, 255, 0))
        icon.putalpha(mask.resize((px, px), Image.LANCZOS))
        out = RES / f"drawable-{density}" / "ic_stat_bug.png"
        out.parent.mkdir(exist_ok=True)
        icon.save(out, optimize=True)

    color.resize((LARGE_PX, LARGE_PX), Image.LANCZOS).save(RES / "drawable-nodpi" / "bug_large.png", optimize=True)


if __name__ == "__main__":
    sys.exit(main())
