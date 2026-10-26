#!/usr/bin/env python3
"""Sort photos into cube directories.

Photos are sorted from oldest to newest (EXIF DateTimeOriginal, falling back to mtime) and split
into cube_NNNN/ directories of 6 photos, named in face order:
    1_white 2_green 3_orange 4_blue 5_red 6_yellow

Numbering continues after the highest existing cube_NNNN in dst, unless --start is given.

Usage:
    python scripts/sort_cubes.py /path/to/photos data/raw/images
    python scripts/sort_cubes.py /path/to/photos data/raw/images --dry-run
    python scripts/sort_cubes.py /path/to/photos data/raw/images --copy  # copy instead of move
    python scripts/sort_cubes.py /path/to/photos data/raw/images --start 26
"""

import argparse
from datetime import UTC, datetime
from pathlib import Path
import re
import shutil
import sys

from PIL import ExifTags, Image

FACES = ["white", "green", "orange", "blue", "red", "yellow"]
FACES_PER_CUBE = len(FACES)

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".heic", ".heif", ".tif", ".tiff"}

EXIF_DATETIME_ORIGINAL = next(k for k, v in ExifTags.TAGS.items() if v == "DateTimeOriginal")

CUBE_DIR_RE = re.compile(r"cube_(\d{4})")


def _exif_time(path: Path) -> datetime | None:
    """EXIF DateTimeOriginal as local time, None if missing or unreadable."""
    try:
        with Image.open(path) as img:
            exif = img.getexif()
            # DateTimeOriginal may be in the main IFD or in the Exif sub-IFD
            dt_raw = exif.get(EXIF_DATETIME_ORIGINAL)
            if dt_raw is None:
                dt_raw = exif.get_ifd(ExifTags.IFD.Exif).get(EXIF_DATETIME_ORIGINAL)
            if not dt_raw:
                return None
            return datetime.strptime(str(dt_raw).strip(), "%Y:%m:%d %H:%M:%S").astimezone()
    except OSError, ValueError:
        return None


def get_capture_time(path: Path) -> tuple[datetime, str]:
    """Return (datetime, source), where source is 'exif' or 'mtime'."""
    dt = _exif_time(path)
    if dt is not None:
        return dt, "exif"
    return datetime.fromtimestamp(path.stat().st_mtime, tz=UTC).astimezone(), "mtime"


def next_cube_number(dst: Path) -> int:
    """Return the number after the highest existing cube_NNNN in dst (1 if none)."""
    if not dst.is_dir():
        return 1
    numbers = [
        int(m.group(1))
        for p in dst.iterdir()
        if p.is_dir() and (m := CUBE_DIR_RE.fullmatch(p.name))
    ]
    return max(numbers, default=0) + 1


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawTextHelpFormatter
    )
    ap.add_argument("src", help="flat directory with photos")
    ap.add_argument("dst", help="target directory, cube_NNNN/ directories are created in it")
    ap.add_argument("--copy", action="store_true", help="copy instead of move (safer)")
    ap.add_argument("--dry-run", action="store_true", help="only print the plan, change nothing")
    ap.add_argument(
        "--start",
        type=int,
        help="number of the first cube (default: after the highest existing cube_NNNN in dst)",
    )
    args = ap.parse_args()

    src = Path(args.src).expanduser().resolve()
    dst = Path(args.dst).expanduser().resolve()

    if not src.is_dir():
        sys.exit(f"ERROR: source directory does not exist: {src}")

    files = [p for p in src.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTS]

    n = len(files)
    if n == 0:
        sys.exit(f"ERROR: no photos found in {src}")

    # Hard requirement: otherwise face assignment would shift for all following cubes
    if n % FACES_PER_CUBE != 0:
        sys.exit(
            f"ERROR: found {n} photos, which is not a multiple of {FACES_PER_CUBE}. "
            f"Check for missing or extra photos. Not splitting, to keep face assignment intact."
        )

    timed = []
    sources = {"exif": 0, "mtime": 0}
    for p in files:
        dt, source = get_capture_time(p)
        sources[source] += 1
        timed.append((dt, p.name, p))

    if sources["mtime"] > 0:
        print(
            f"WARNING: {sources['mtime']}/{n} photos have NO EXIF DateTimeOriginal - file time "
            f"(mtime) was used, which is unreliable after copying/uploading. "
            f"Verify the order of these cubes!"
        )
    print(f"Time source: EXIF={sources['exif']}, mtime={sources['mtime']}")

    # Oldest first; ties broken by file name (deterministic)
    timed.sort(key=lambda t: (t[0], t[1]))

    n_cubes = n // FACES_PER_CUBE
    start = args.start if args.start is not None else next_cube_number(dst)
    if start < 1:
        sys.exit(f"ERROR: --start must be >= 1, got {start}")
    cube_names = [f"cube_{i:04d}" for i in range(start, start + n_cubes)]

    existing = [name for name in cube_names if (dst / name).exists()]
    if existing:
        sys.exit(f"ERROR: target directories already exist: {', '.join(existing)}")

    print(f"Photos: {n}  ->  cubes: {n_cubes} ({FACES_PER_CUBE} each)")
    print(f"Cubes: {cube_names[0]} .. {cube_names[-1]}")
    print("=" * 60)

    op = shutil.copy2 if args.copy else shutil.move
    op_name = "COPIED" if args.copy else "MOVED"

    for cube_idx, cube_name in enumerate(cube_names):
        cube_dir = dst / cube_name
        chunk = timed[cube_idx * FACES_PER_CUBE : (cube_idx + 1) * FACES_PER_CUBE]

        print(f"\n{cube_name}/")
        if not args.dry_run:
            cube_dir.mkdir(parents=True)

        for face_idx, (dt, _name, srcpath) in enumerate(chunk):
            ext = srcpath.suffix.lower()
            if ext == ".jpeg":
                ext = ".jpg"
            newname = f"{face_idx + 1}_{FACES[face_idx]}{ext}"
            print(f"  {dt:%Y-%m-%d %H:%M:%S}  {srcpath.name:30s} -> {newname}")
            if not args.dry_run:
                op(str(srcpath), str(cube_dir / newname))

    print("\n" + "=" * 60)
    if args.dry_run:
        print("DRY-RUN: nothing changed. Run without --dry-run to execute.")
    else:
        print(f"Done ({op_name}). Directories in: {dst}")


if __name__ == "__main__":
    main()
