#!/usr/bin/env python3
"""Cuts a piece out of a screenshot and magnifies it, so a detail can be looked at.

A shot from `shots.sh` is 854x480, which is the size the game was played at and far too small
to judge a badge or a shadow by. This blows a named rectangle up by whole pixels - nearest
neighbour, never smoothed, because a smoothed pixel is a different pixel and the whole point
is to see the ones that are really there.

    tools/crop.py <shot.png> <out.png> <x0> <y0> <x1> <y1> [scale]

Standard library only, so it works wherever the gate does.
"""
import struct
import sys
import zlib


def read_png(path):
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'{path} is not a PNG')
    at, width, height, colour, idat = 8, None, None, None, b''
    while at < len(data):
        length = struct.unpack('>I', data[at:at + 4])[0]
        kind, body = data[at + 4:at + 8], data[at + 8:at + 8 + length]
        if kind == b'IHDR':
            width, height, depth, colour = struct.unpack('>IIBB', body[:10])
            if depth != 8:
                raise SystemExit('only 8 bits a channel')
        elif kind == b'IDAT':
            idat += body
        elif kind == b'IEND':
            break
        at += 8 + length + 4

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw, stride = zlib.decompress(idat), width * channels
    out, previous, at = bytearray(), bytearray(stride), 0
    for _ in range(height):
        filtered, line = raw[at], bytearray(raw[at + 1:at + 1 + stride])
        at += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = previous[x]
            c = previous[x - channels] if x >= channels else 0
            if filtered == 1:
                line[x] = (line[x] + a) & 255
            elif filtered == 2:
                line[x] = (line[x] + b) & 255
            elif filtered == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif filtered == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out += line
        previous = line
    return width, height, channels, bytes(out)


def write_png(path, width, height, channels, pixels):
    raw = b''.join(b'\x00' + pixels[y * width * channels:(y + 1) * width * channels]
                   for y in range(height))

    def chunk(kind, body):
        return (struct.pack('>I', len(body)) + kind + body
                + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff))

    header = struct.pack('>IIBBBBB', width, height, 8, {1: 0, 3: 2, 4: 6}[channels], 0, 0, 0)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                           + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


def main(argv):
    if len(argv) not in (7, 8):
        raise SystemExit(__doc__)
    source, target = argv[1], argv[2]
    x0, y0, x1, y1 = (int(one) for one in argv[3:7])
    scale = int(argv[7]) if len(argv) == 8 else 4

    width, height, channels, pixels = read_png(source)
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(width, x1), min(height, y1)
    if x1 <= x0 or y1 <= y0:
        raise SystemExit('that rectangle is not on the picture')

    wide, high = (x1 - x0) * scale, (y1 - y0) * scale
    out = bytearray()
    for y in range(high):
        row = y0 + y // scale
        for x in range(wide):
            at = (row * width + x0 + x // scale) * channels
            out += pixels[at:at + channels]
    write_png(target, wide, high, channels, bytes(out))
    print(f'{target} {wide}x{high}, {scale}x of {x0},{y0}-{x1},{y1}')


if __name__ == '__main__':
    main(sys.argv)
