"""Check ELF load alignment in an APK, including the wrapper's extracted ZIPs."""

import argparse
from collections import Counter
from io import BytesIO
from pathlib import Path
from struct import unpack_from
from zipfile import ZipFile


def load_segments(data):
    if not data.startswith(b"\x7fELF"):
        return None
    if data[4:6] != b"\x02\x01":
        raise ValueError("Expected little-endian 64-bit ELF")
    start = unpack_from("<Q", data, 32)[0]
    size, count = unpack_from("<HH", data, 54)
    if size < 56 or start + size * count > len(data):
        raise ValueError("Invalid ELF program headers")
    segments = []
    for offset in range(start, start + size * count, size):
        kind, _, file_offset, virtual_address, _, _, _, alignment = unpack_from("<IIQQQQQQ", data, offset)
        if kind == 1:  # PT_LOAD
            segments.append((file_offset, virtual_address, alignment))
    if not segments:
        raise ValueError("ELF has no loadable segments")
    return segments


def native_files(apk):
    for entry in apk.namelist():
        if not entry.startswith("lib/") or not entry.endswith(".so"):
            continue
        abi = entry.split("/")[1]
        data = apk.read(entry)
        if entry.endswith(".zip.so"):
            with ZipFile(BytesIO(data)) as nested:
                for path in nested.namelist():
                    if not path.endswith("/") and ".so" in Path(path).name:
                        segments = load_segments(nested.read(path))
                        if segments is not None:  # Symlinks in the bundle are not ELF files.
                            yield abi, f"{entry}!/{path}", segments
        else:
            segments = load_segments(data)
            if segments is None:
                raise ValueError(f"Not an ELF or supported ZIP: {entry}")
            yield abi, entry, segments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--page-size", type=int, choices=(4_096, 16_384), default=16_384)
    args = parser.parse_args()
    counts = Counter()
    failures = 0
    with ZipFile(args.apk) as apk:
        for abi, name, segments in native_files(apk):
            counts[abi] += 1
            if any(a < args.page_size or offset % args.page_size != va % args.page_size for offset, va, a in segments):
                print(f"FAIL {name}: minimum PT_LOAD alignment {min(a for _, _, a in segments):,} bytes")
                failures += 1
    print(f"{args.apk.name}: {sum(counts.values())} ELF files, {failures} failing at {args.page_size:,} bytes; {dict(counts)}")
    return int(bool(failures or not counts))


if __name__ == "__main__":
    raise SystemExit(main())
