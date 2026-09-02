#!/usr/bin/env python3
"""Draws the villager's UV map, so a profession texture can be painted by looking.

The layout is not guessable and not documented anywhere in the game's files: it falls out
of the box list in VillagerModel.createBodyModel(), where each cube at texOffs(u, v) with
size (w, h, d) claims six rectangles in a fixed arrangement around that corner. Getting one
of them wrong means painting a sleeve onto the back of a head, which looks like nothing at
all until the model turns round.

The numbers below are copied from 1.21.1's VillagerModel and nothing else. Re-run this if
the model ever changes; do not hand-correct the picture.

    python3 tools/villager_guide.py
"""
from PIL import Image, ImageDraw

SIZE, SCALE = 64, 10

# name -> (texOffs u, v, width, height, depth), straight out of VillagerModel
PARTS = [
    ("head",     0,  0,  8, 10, 8, (232,  90,  70)),
    ("hat",     32,  0,  8, 10, 8, ( 74, 144, 226)),
    ("hat rim", 30, 47, 16, 16, 1, ( 46,  96, 160)),
    ("nose",    24,  0,  2,  4, 2, (240, 168,  50)),
    ("body",    16, 20,  8, 12, 6, ( 96, 176,  96)),
    ("jacket",   0, 38,  8, 20, 6, ( 60, 128,  70)),
    ("arm",     44, 22,  4,  8, 4, (168, 110, 200)),
    ("arm bar", 40, 38,  8,  4, 4, (140,  88, 170)),
    ("leg",      0, 22,  4, 12, 4, (200, 150, 110)),
]

def faces(u, v, w, h, d):
    """The six rectangles a cube claims, in Minecraft's own order."""
    return [
        ("top",    u + d,         v,     w, d),
        ("bottom", u + d + w,     v,     w, d),
        ("right",  u,             v + d, d, h),
        ("front",  u + d,         v + d, w, h),
        ("left",   u + d + w,     v + d, d, h),
        ("back",   u + d + w + d, v + d, w, h),
    ]

def main():
    img = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), (24, 24, 28, 255))
    draw = ImageDraw.Draw(img, "RGBA")

    # A pixel grid, so a coordinate can be counted off the picture rather than guessed.
    for n in range(SIZE + 1):
        shade = (70, 70, 78, 255) if n % 8 == 0 else (44, 44, 50, 255)
        draw.line([(n * SCALE, 0), (n * SCALE, SIZE * SCALE)], fill=shade)
        draw.line([(0, n * SCALE), (SIZE * SCALE, n * SCALE)], fill=shade)

    for name, u, v, w, h, d, color in PARTS:
        for face, fx, fy, fw, fh in faces(u, v, w, h, d):
            box = [fx * SCALE, fy * SCALE, (fx + fw) * SCALE - 1, (fy + fh) * SCALE - 1]
            draw.rectangle(box, fill=color + (58,), outline=color + (255,), width=2)
            # The front faces are the ones anybody actually paints, so they say who they are.
            if face in ("front", "right", "left", "back") and fw >= 4 and fh >= 4:
                draw.text((box[0] + 4, box[1] + 3), name, fill=(255, 255, 255, 235))
                draw.text((box[0] + 4, box[1] + 15), face, fill=(255, 255, 255, 150))
    img.save("art/villager/uv-guide.png")
    print("art/villager/uv-guide.png")

    # And the same rectangles as numbers, for anyone reading rather than looking.
    for name, u, v, w, h, d, _ in PARTS:
        where = "  ".join(f"{f}({x},{y}) {fw}x{fh}" for f, x, y, fw, fh in faces(u, v, w, h, d))
        print(f"{name:9} texOffs({u},{v}) {w}x{h}x{d}\n          {where}")

if __name__ == "__main__":
    main()
