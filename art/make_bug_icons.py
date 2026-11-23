"""Regenerates notification icons from bug8.svg. Needs rsvg-convert, Pillow and numpy.

Small icon: Android uses only the alpha channel, so dark outlines are cut out to keep eyes and wings readable.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

ART = Path(__file__).resolve().parent
RES = ART.parent / "app/src/main/res"
SMALL_DP = 24
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LARGE_PX = 256
OUTLINE_LUMA = 0.18
OUTLINE_RAMP = 0.05


def square(image: Image.Image) -> Image.Image:
    side = max(image.size)
    canvas = Image.new(image.mode, (side, side))
    canvas.paste(image, ((side - image.width) // 2, (side - image.height) // 2))
    return canvas


def main() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        rendered = Path(tmp) / "bug.png"
        subprocess.run(["rsvg-convert", "-h", "1024", str(ART / "bug8.svg"), "-o", str(rendered)], check=True)
        color = square(Image.open(rendered).convert("RGBA"))

    pixels = np.asarray(color).astype(float) / 255
    luma = pixels[..., :3] @ [0.299, 0.587, 0.114]
    alpha = pixels[..., 3] * np.clip((luma - OUTLINE_LUMA) / OUTLINE_RAMP, 0, 1)
    mask = Image.fromarray((alpha * 255).astype(np.uint8))

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
