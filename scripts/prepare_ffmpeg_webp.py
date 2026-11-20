"""Make the app's WebP replacement ZIP from the two pinned Termux packages."""

import argparse
from hashlib import sha256
from io import BytesIO
from pathlib import Path, PurePosixPath
from tarfile import open as open_tar
from zipfile import ZIP_STORED, ZipFile, ZipInfo

from check_native_alignment import load_segments

LIBRARIES = ("libsharpyuv", "libwebp", "libwebpdecoder", "libwebpdemux", "libwebpmux")
PACKAGES = {
    "arm64-v8a": "8ba7fcfadd1a9957a7632c6407d6ef3e41d34dbd96a41769b245592ed4559045",
    "x86_64": "3274f3b6b3b765ed5cfb3d33cf309a2222de7766ad53f0eaefcbe89563f2f7a3",
}


def read_libraries(path, expected_hash):
    data = path.read_bytes()
    if sha256(data).hexdigest() != expected_hash or not data.startswith(b"!<arch>\n"):
        raise ValueError(f"Unexpected Debian package: {path}")
    position = 8
    while position + 60 <= len(data):
        name = data[position:position + 16].strip().rstrip(b"/")
        size = int(data[position + 48:position + 58])
        position += 60
        if name == b"data.tar.xz":
            with open_tar(fileobj=BytesIO(data[position:position + size]), mode="r:xz") as archive:
                members = {PurePosixPath(m.name).name: m for m in archive if m.isfile()}
                for library in LIBRARIES:
                    filename = f"{library}.so"
                    with archive.extractfile(members[filename]) as source:
                        binary = source.read()
                    segments = load_segments(binary)
                    if not segments or any(a < 16384 or (o - v) % 16384 for o, v, a in segments):
                        raise ValueError(f"Missing 16 KB alignment: {path.name}/{filename}")
                    yield filename, binary
            return
        position += size + size % 2
    raise ValueError(f"No data.tar.xz in {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aarch64_deb", type=Path)
    parser.add_argument("x86_64_deb", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    libraries = [
        (f"{abi}/{filename}", data)
        for (abi, digest), path in zip(PACKAGES.items(), (args.aarch64_deb, args.x86_64_deb))
        for filename, data in read_libraries(path, digest)
    ]
    # Store entries uncompressed so regeneration is independent of zlib version.
    with ZipFile(args.output, "x", compression=ZIP_STORED) as bundle:
        for name, data in libraries:
            bundle.writestr(ZipInfo(name), data)


if __name__ == "__main__":
    main()
