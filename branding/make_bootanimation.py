"""Yeti TV OS boot animation, built from the power-on logo so the hand-off is seamless.

Every frame is the u-boot logo frame (yeti_bootup.bmp, 1920x1080) pixel for pixel, plus a "loading" line under
the logo: three pulsing dots and "Starting Yeti TV OS". Android's bootanimation does not scale frames, so the
animation must be full 1920x1080 or the logo jumps in size when it starts.

Also writes app/src/main/res/drawable-nodpi/splash_logo.png (the logo crop) for the launcher's window
background, so the screen keeps showing the same image when the animation stops.

Usage:  python branding/make_bootanimation.py        (from the repo root; needs Pillow + NumPy)
Output: branding/bootanimation.zip  ->  /oem/media/bootanimation.zip on the box
"""
import math
import os
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FPS, FRAMES = 15, 15                      # 1 s loop; 1080p PNG decode on a Cortex-A35 can't sustain 30 fps
ACCENT = (32, 160, 255)

base = Image.open(os.path.join(HERE, "yeti_bootup.bmp")).convert("RGB")
W, H = base.size
bg = tuple(int(v) for v in np.asarray(base)[5, 5])
arr = np.asarray(base).astype(int)
ys, xs = np.where(np.abs(arr - np.array(bg)).sum(2) > 12)
logo_box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
cy = logo_box[3] + 95                     # loader line, under the logo

try:
    font = ImageFont.truetype("segoeui.ttf", 30)
except OSError:
    font = ImageFont.load_default()
label = "Starting Yeti TV OS"


def frame(i):
    im = base.copy()
    d = ImageDraw.Draw(im)
    tw = d.textlength(label, font=font)
    gap, r = 26, 7
    dots_w = 2 * gap
    total = dots_w + 28 + tw
    x0 = (W - total) / 2
    for k in range(3):
        # each dot breathes, staggered by a third of the loop
        p = 0.5 + 0.5 * math.sin(2 * math.pi * (i / FRAMES - k / 3))
        a = 0.25 + 0.75 * p
        col = tuple(int(bg[c] + (ACCENT[c] - bg[c]) * a) for c in range(3))
        rr = r * (0.75 + 0.35 * p)
        cx = x0 + k * gap
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=col)
    dim = tuple(int(bg[c] + (200 - bg[c]) * 0.75) for c in range(3))
    d.text((x0 + dots_w + 28, cy), label, font=font, fill=dim, anchor="lm")
    return im


os.makedirs(os.path.join(HERE, "anim", "part0"), exist_ok=True)
with zipfile.ZipFile(os.path.join(HERE, "bootanimation.zip"), "w", zipfile.ZIP_STORED) as z:
    z.writestr("desc.txt", "%d %d %d\np 0 0 part0 #%02X%02X%02X\n" % ((W, H, FPS) + bg))
    for i in range(FRAMES):
        p = os.path.join(HERE, "anim", "part0", "%03d.png" % i)
        frame(i).save(p, optimize=True)
        z.write(p, "part0/%03d.png" % i)

crop = base.crop(logo_box)
crop.save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "splash_logo.png"), optimize=True)
print("bootanimation.zip", os.path.getsize(os.path.join(HERE, "bootanimation.zip")), "bytes;",
      "bg #%02X%02X%02X" % bg, "logo box", logo_box, "loader y", cy)
