#!/usr/bin/env python3
"""Update patches: the new APK as "copy these bytes from the installed APK" and "these new bytes".

    python3 tools/apk_patch.py make OLD.apk NEW.apk OUT.patch   # writes OUT.patch only if it is worth it
    python3 tools/apk_patch.py apply OLD.apk PATCH OUT.apk

The app (update/ApkPatch.kt) rebuilds the new APK from the installed one and the patch, checks it is byte for
byte the APK CI signed (SHA-256), and installs that; Android still installs a whole APK, but only the changed
part is downloaded. An APK is a zip: an entry that did not change is copied as is, a stored (uncompressed) data
file that changed is copied line by line where its lines are unchanged (the dictionaries in assets/), and the
rest (headers, signature, the compressed code) is new bytes.

Format: b"HJPATCH1", then one zlib stream of: old size (u64), old SHA-256, new size (u64), new SHA-256, then
operations: b"C" offset u64 length u64 (copy from the old APK), b"D" length u64 bytes (new bytes), b"E" (end).
All integers big-endian.
"""
import hashlib
import struct
import sys
import zipfile
import zlib

MAGIC = b"HJPATCH1"
# A patch at least this share of the full APK is not worth the extra step.
WORTH = 0.6


def entries(path, data):
    """name -> (data offset, data length, compress type, crc) for every entry."""
    out = {}
    with zipfile.ZipFile(path) as z:
        for i in z.infolist():
            h = i.header_offset
            if data[h:h + 4] != b"PK\x03\x04":
                raise ValueError("bad local header: " + i.filename)
            n, m = struct.unpack("<HH", data[h + 26:h + 30])
            start = h + 30 + n + m
            out[i.filename] = (start, i.compress_size, i.compress_type, i.CRC)
    return out


class Ops:
    def __init__(self):
        self.ops = []

    def copy(self, off, length):
        if length <= 0:
            return
        if self.ops and self.ops[-1][0] == "C" and self.ops[-1][1] + self.ops[-1][2] == off:
            self.ops[-1][2] += length
        else:
            self.ops.append(["C", off, length])

    def data(self, b):
        if not b:
            return
        if self.ops and self.ops[-1][0] == "D":
            self.ops[-1][1].extend(b)
        else:
            self.ops.append(["D", bytearray(b)])


def lines(b):
    out, start = [], 0
    while start < len(b):
        end = b.find(b"\n", start)
        end = len(b) if end < 0 else end + 1
        out.append((start, b[start:end]))
        start = end
    return out


def diff_lines(ops, old, old_start, old_len, new):
    """A stored file: each new line copied from where the same line is in the old file, preferring the next line."""
    where = {}
    for off, line in lines(old[old_start:old_start + old_len]):
        where.setdefault(line, off)
    expect = None
    for _, line in lines(new):
        off = None
        if expect is not None and old[old_start + expect:old_start + expect + len(line)] == line:
            off = expect
        elif line in where:
            off = where[line]
        if off is None:
            ops.data(line)
            expect = None
        else:
            ops.copy(old_start + off, len(line))
            expect = off + len(line)


def make_ops(old_path, new_path):
    old = open(old_path, "rb").read()
    new = open(new_path, "rb").read()
    eo, en = entries(old_path, old), entries(new_path, new)
    ops = Ops()
    pos = 0
    for name, (start, length, kind, crc) in sorted(en.items(), key=lambda e: e[1][0]):
        ops.data(new[pos:start])
        piece = new[start:start + length]
        o = eo.get(name)
        if o and o[1] == length and o[2] == kind and o[3] == crc and old[o[0]:o[0] + length] == piece:
            ops.copy(o[0], length)
        elif o and kind == 0 and o[2] == 0:
            diff_lines(ops, old, o[0], o[1], piece)
        else:
            ops.data(piece)
        pos = start + length
    ops.data(new[pos:])
    return old, new, ops.ops


def encode(old, new, ops):
    body = bytearray()
    body += struct.pack(">Q", len(old)) + hashlib.sha256(old).digest()
    body += struct.pack(">Q", len(new)) + hashlib.sha256(new).digest()
    for op in ops:
        if op[0] == "C":
            body += b"C" + struct.pack(">QQ", op[1], op[2])
        else:
            body += b"D" + struct.pack(">Q", len(op[1])) + op[1]
    body += b"E"
    return MAGIC + zlib.compress(bytes(body), 9)


def apply(old, patch):
    if patch[:8] != MAGIC:
        raise ValueError("not a patch")
    body = zlib.decompress(patch[8:])
    old_size, = struct.unpack(">Q", body[0:8])
    if old_size != len(old) or body[8:40] != hashlib.sha256(old).digest():
        raise ValueError("patch is for another APK")
    new_size, = struct.unpack(">Q", body[40:48])
    want = body[48:80]
    out, i = bytearray(), 80
    while True:
        op = body[i:i + 1]
        i += 1
        if op == b"C":
            off, length = struct.unpack(">QQ", body[i:i + 16])
            i += 16
            if off + length > len(old):
                raise ValueError("copy out of range")
            out += old[off:off + length]
        elif op == b"D":
            length, = struct.unpack(">Q", body[i:i + 8])
            i += 8
            out += body[i:i + length]
            i += length
        elif op == b"E":
            break
        else:
            raise ValueError("bad operation")
    if len(out) != new_size or hashlib.sha256(out).digest() != want:
        raise ValueError("result differs from the signed APK")
    return bytes(out)


def main(argv):
    if len(argv) == 5 and argv[1] == "make":
        old, new, ops = make_ops(argv[2], argv[3])
        patch = encode(old, new, ops)
        if apply(old, patch) != new:
            raise SystemExit("patch does not rebuild the APK")
        share = len(patch) / len(new)
        print(f"{argv[4]}: {len(patch):,} bytes ({share:.1%} of {len(new):,})")
        if share < WORTH:
            open(argv[4], "wb").write(patch)
        else:
            print("not worth it: no patch written")
    elif len(argv) == 5 and argv[1] == "apply":
        open(argv[4], "wb").write(apply(open(argv[2], "rb").read(), open(argv[3], "rb").read()))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
