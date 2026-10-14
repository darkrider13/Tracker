"""Make the Android *native* splash match the in-app (web) boot splash.

Problem: on launch Android first shows its own splash (the big launcher badge),
then the WebView's #boot-splash appears with a 120px icon, so the icon seemed
to jump from big to small.

Fix: draw the native splash icon at exactly the web splash size and position
(120dp image, nudged up to sit above the tagline), use the same background
colours, and blank any legacy full-screen splash.png so no oversized logo shows.

Usage: python3 scripts/native_splash.py android/app/src/main/res
"""
import glob, os, re, sys
from PIL import Image

RES = sys.argv[1] if len(sys.argv) > 1 else 'android/app/src/main/res'
SRC = 'app-media/splash-icon.png'   # same file the web splash uses

DARK, LIGHT = '#05070a', '#ffffff'   # same as #boot-splash backgrounds
WEB_ICON_DP = 120                    # #boot-splash img width/height
CANVAS_DP = 288                      # Android splash icon canvas
LIFT_DP = 22                         # web splash centres icon+tagline as a group

DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

src = Image.open(SRC).convert('RGBA')

# 1) splash icon, one PNG per density, canvas = 288dp
for name, d in DENSITIES.items():
    canvas_px = int(CANVAS_DP * d)
    icon_px = int(round(WEB_ICON_DP * d))
    canvas = Image.new('RGBA', (canvas_px, canvas_px), (0, 0, 0, 0))
    icon = src.resize((icon_px, icon_px), Image.LANCZOS)
    x = (canvas_px - icon_px) // 2
    y = (canvas_px - icon_px) // 2 - int(round(LIFT_DP * d))
    canvas.alpha_composite(icon, (x, y))
    out = os.path.join(RES, 'drawable-' + name)
    os.makedirs(out, exist_ok=True)
    canvas.save(os.path.join(out, 'splash_icon.png'))

# 2) colours (follow the system day/night like the default splash did)
for folder, color in (('values', LIGHT), ('values-night', DARK)):
    os.makedirs(os.path.join(RES, folder), exist_ok=True)
    with open(os.path.join(RES, folder, 'splash_colors.xml'), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <color name="app_splash_bg">%s</color>\n</resources>\n' % color)

# 3) point the launch theme at them
styles = os.path.join(RES, 'values', 'styles.xml')
c = open(styles).read()
items = ('\n        <item name="windowSplashScreenBackground">@color/app_splash_bg</item>'
         '\n        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>')
m = re.search(r'<style\s+name="AppTheme\.NoActionBarLaunch"[^>]*>', c)
if not m:
    print('AppTheme.NoActionBarLaunch not found - styles.xml left unchanged')
elif 'app_splash_bg' in c:
    print('splash theme already patched')
else:
    c = c[:m.end()] + items + c[m.end():]
    open(styles, 'w').write(c)
    print('launch theme patched')

# 4) blank legacy full-screen splash.png files (old Capacitor logo splash)
n = 0
for p in glob.glob(os.path.join(RES, 'drawable*', 'splash.png')):
    w, h = Image.open(p).size
    rgb = DARK if 'night' in p else LIGHT
    Image.new('RGB', (w, h), rgb).save(p)
    n += 1
print('native splash ready; blanked %d legacy splash.png' % n)
