"""A minimal NBT reader and writer, enough for structure templates.

Vanilla's structure format is a small, stable subset of NBT: a compound holding a size, a
palette of block states, a list of blocks, and a data version. This reads and writes that and
nothing clever, so tools/village.py can produce a building without the repository taking on a
dependency to move a few thousand integers around.

Round-tripping a real vanilla structure through this produces the same blocks, which is the
only claim it makes.
"""
import gzip, struct
from collections import OrderedDict

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)

class Tag:
    def __init__(self, kind, value):
        self.kind = kind
        self.value = value
    def __repr__(self):
        return f"Tag({self.kind},{self.value!r})"

class R:
    def __init__(self, b): self.b, self.i = b, 0
    def take(self, n):
        v = self.b[self.i:self.i+n]; self.i += n; return v
    def u1(self): return self.take(1)[0]
    def i1(self): return struct.unpack('>b', self.take(1))[0]
    def i2(self): return struct.unpack('>h', self.take(2))[0]
    def u2(self): return struct.unpack('>H', self.take(2))[0]
    def i4(self): return struct.unpack('>i', self.take(4))[0]
    def i8(self): return struct.unpack('>q', self.take(8))[0]
    def f4(self): return struct.unpack('>f', self.take(4))[0]
    def f8(self): return struct.unpack('>d', self.take(8))[0]
    def s(self):
        return self.take(self.u2()).decode('utf-8')

def read_payload(r, kind):
    if kind == BYTE: return r.i1()
    if kind == SHORT: return r.i2()
    if kind == INT: return r.i4()
    if kind == LONG: return r.i8()
    if kind == FLOAT: return r.f4()
    if kind == DOUBLE: return r.f8()
    if kind == BYTES: return r.take(r.i4())
    if kind == STRING: return r.s()
    if kind == LIST:
        inner = r.u1(); n = r.i4()
        return (inner, [read_payload(r, inner) for _ in range(n)])
    if kind == COMPOUND:
        out = OrderedDict()
        while True:
            k = r.u1()
            if k == END: return out
            name = r.s()
            out[name] = Tag(k, read_payload(r, k))
    if kind == INTS: return [r.i4() for _ in range(r.i4())]
    if kind == LONGS: return [r.i8() for _ in range(r.i4())]
    raise ValueError(f"unknown tag {kind}")

def read(path):
    raw = open(path, 'rb').read()
    if raw[:2] == b'\x1f\x8b':
        raw = gzip.decompress(raw)
    r = R(raw)
    kind = r.u1()
    name = r.s()
    return name, Tag(kind, read_payload(r, kind))

class W:
    def __init__(self): self.out = bytearray()
    def u1(self, v): self.out.append(v & 0xFF)
    def i2(self, v): self.out += struct.pack('>h', v)
    def u2(self, v): self.out += struct.pack('>H', v)
    def i4(self, v): self.out += struct.pack('>i', v)
    def i8(self, v): self.out += struct.pack('>q', v)
    def s(self, v):
        b = v.encode('utf-8'); self.u2(len(b)); self.out += b

def write_payload(w, kind, value):
    if kind == BYTE: w.out += struct.pack('>b', value)
    elif kind == SHORT: w.i2(value)
    elif kind == INT: w.i4(value)
    elif kind == LONG: w.i8(value)
    elif kind == FLOAT: w.out += struct.pack('>f', value)
    elif kind == DOUBLE: w.out += struct.pack('>d', value)
    elif kind == BYTES:
        w.i4(len(value)); w.out += bytes(value)
    elif kind == STRING: w.s(value)
    elif kind == LIST:
        inner, items = value
        w.u1(inner); w.i4(len(items))
        for it in items: write_payload(w, inner, it)
    elif kind == COMPOUND:
        for name, tag in value.items():
            w.u1(tag.kind); w.s(name); write_payload(w, tag.kind, tag.value)
        w.u1(END)
    elif kind == INTS:
        w.i4(len(value))
        for v in value: w.i4(v)
    elif kind == LONGS:
        w.i4(len(value))
        for v in value: w.i8(v)
    else: raise ValueError(f"unknown tag {kind}")

def write(path, root, name=""):
    w = W()
    w.u1(root.kind); w.s(name); write_payload(w, root.kind, root.value)
    with gzip.GzipFile(path, 'wb', mtime=0) as f:
        f.write(bytes(w.out))
