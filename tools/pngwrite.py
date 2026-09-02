#!/usr/bin/env python3
"""Writes a PNG, and refuses to write over one somebody painted.

No dependencies: PNGs go out through zlib and struct, so this runs anywhere Python does -
which is the reason the generators here do not need Pillow to write what they draw.

Kept in one place because more than one script writes textures and they all have to agree
about the protected list. Take a file off that list when you want a generator to own it
again; put one on it the moment a generated texture is repainted by hand.
"""
import os
import struct
import zlib


#: Art that is somebody's rather than a script's.
#:
#: Every one of these was generated once and has since been repainted by hand. A generator
#: run again would put the flat version back silently, and the only sign would be a texture
#: that used to be good going plain - so writing to one of these is an error instead.
PROTECTED = {
    "block/table_felt.png",
    "item/pack.png",
    "item/sealed.png",
}


def rgba(color, alpha=255):
    if len(color) == 4:
        return color
    return color + (alpha,)


def isProtected(path):
    tail = path.replace("\\", "/").split("/textures/", 1)
    return len(tail) == 2 and tail[1] in PROTECTED


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a)."""
    if isProtected(path):
        raise SystemExit(
            "refusing to overwrite " + path + "\n"
            "It is hand-painted; see PROTECTED at the top of tools/pngwrite.py.")
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter: none
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)
    print("wrote", path, f"({width}x{height})")


def write_mcmeta(path, border, width, height):
    with open(path + ".mcmeta", "w") as handle:
        handle.write(
            '{\n  "gui": {\n    "scaling": {\n      "type": "nine_slice",\n'
            f'      "width": {width},\n      "height": {height},\n'
            f'      "border": {border}\n'
            "    }\n  }\n}\n"
        )


def blank(width, height):
    return [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]
