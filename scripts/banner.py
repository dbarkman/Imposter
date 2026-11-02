import sys
from PIL import Image, ImageDraw, ImageFont
fonts, out = sys.argv[1], sys.argv[2]
S = 4
W, H = 640 * S, 360 * S
img = Image.new("RGB", (W, H), (23, 18, 31))
d = ImageDraw.Draw(img)
# soft red glow band
for i in range(60):
    pass
cx, cy = 135 * S, 180 * S
# eye almond
import math
pts = []
for t in range(0, 361, 3):
    a = math.radians(t)
    x = cx + 78 * S * math.cos(a)
    y = cy + 50 * S * math.sin(a) * (abs(math.sin(a)) ** 0.15)
    pts.append((x, y))
d.ellipse((cx - 92 * S, cy - 92 * S, cx + 92 * S, cy + 92 * S), fill=(255, 77, 94))
d.polygon(pts, fill=(23, 18, 31))
d.ellipse((cx - 30 * S, cy - 30 * S, cx + 30 * S, cy + 30 * S), fill=(255, 255, 255))
d.ellipse((cx - 13 * S, cy - 13 * S, cx + 13 * S, cy + 13 * S), fill=(23, 18, 31))
f = ImageFont.truetype(fonts + "/bricolage.ttf", 82 * S)
try:
    f.set_variation_by_axes([800])
except Exception as e:
    print("variation", e)
d.text((258 * S, 176 * S), "Imposter", font=f, fill=(247, 242, 255), anchor="lm")
img = img.resize((640, 360), Image.LANCZOS)
img.save(out)
