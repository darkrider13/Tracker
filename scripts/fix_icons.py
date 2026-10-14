"""Rebuild launcher icons so the badge fills the icon.

@capacitor/assets shrinks the foreground a lot, so the badge looked tiny.
This runs right after it and rewrites the foreground (adaptive) and legacy
icons from resources/, with the badge sized to the adaptive safe zone.
"""
import glob, os, sys
from PIL import Image, ImageDraw

RES = sys.argv[1] if len(sys.argv) > 1 else 'android/app/src/main/res'
ADAPTIVE_FILL = 0.80   # badge width as a share of the 108dp layer (visible window is ~0.667)
LEGACY_FILL = 0.78     # badge width as a share of the legacy icon
RADIUS = 0.22          # legacy icon corner radius
SS = 4

fg_src = Image.open('resources/icon-foreground.png').convert('RGBA')
bbox = fg_src.getchannel('A').getbbox()
badge = fg_src.crop(bbox)                       # tight crop of the badge
bg_src = Image.open('resources/icon-background.png').convert('RGBA')

def place(size, fill):
    w = int(round(size * fill))
    h = int(round(w * badge.height / badge.width))
    b = badge.resize((w, h), Image.LANCZOS)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(b, ((size - w) // 2, (size - h) // 2))
    return canvas

def rounded(im, radius):
    w, h = im.size
    m = Image.new('L', (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * SS - 1, h * SS - 1),
                                        radius=int(min(w, h) * SS * radius), fill=255)
    m = m.resize((w, h), Image.LANCZOS)
    im.putalpha(Image.composite(im.getchannel('A'), Image.new('L', (w, h), 0), m))
    return im

n = 0
for d in sorted(glob.glob(os.path.join(RES, 'mipmap-*'))):
    fg = os.path.join(d, 'ic_launcher_foreground.png')
    if os.path.exists(fg):                       # adaptive layer (108dp)
        size = Image.open(fg).size[0]
        place(size, ADAPTIVE_FILL).save(fg); n += 1
    for name in ('ic_launcher.png', 'ic_launcher_round.png'):   # legacy
        p = os.path.join(d, name)
        if not os.path.exists(p):
            continue
        size = Image.open(p).size[0]
        im = bg_src.resize((size, size), Image.LANCZOS)
        im.alpha_composite(place(size, LEGACY_FILL))
        if name == 'ic_launcher_round.png':
            im = rounded(im, 0.5)
        else:
            im = rounded(im, RADIUS)
        im.save(p); n += 1
print('icons rebuilt:', n)
