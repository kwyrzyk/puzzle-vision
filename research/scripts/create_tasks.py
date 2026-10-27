#!/usr/bin/env python3
"""Create one CVAT task per cube directory (cube_NNNN/).

Each task gets the 6 face photos in order 1_white..6_yellow and a schema of 7 labels
(face + 6 colors with a position attribute).

Idempotent: cubes whose task with the same name already exists on the server are skipped,
so it can be run repeatedly without creating duplicates.

Requires cvat-sdk from the optional `cvat` dependency group (matches the CVAT server version).

Usage:
    uv run --group cvat python scripts/create_tasks.py data/raw/images \
        --host http://localhost:8080 --user LOGIN

    # preview only (recommended first):
    uv run --group cvat python scripts/create_tasks.py data/raw/images \
        --host http://localhost:8080 --user LOGIN --dry-run

    # only cubes 5..10 (inclusive):
    uv run --group cvat python scripts/create_tasks.py data/raw/images \
        --host http://localhost:8080 --user LOGIN --from 5 --to 10

    # into a project (tasks inherit its labels):
    uv run --group cvat python scripts/create_tasks.py data/raw/images \
        --host http://localhost:8080 --user LOGIN --project-id 1
"""

import argparse
import getpass
from pathlib import Path
import re
import sys

# Face order = file order in the task
FACES = ["white", "green", "orange", "blue", "red", "yellow"]
FACES_PER_CUBE = len(FACES)
IMAGE_EXTS = {".jpg", ".jpeg", ".png"}

POSITIONS = ["unset", "center", "edge", "corner"]


def cube_number(name: str) -> int | None:
    """Number from a directory name, cube_0007 -> 7; None if there is none."""
    m = re.search(r"cube_(\d+)", name)
    return int(m.group(1)) if m else None


def find_cube_dirs(
    root: Path, lo: int | None = None, hi: int | None = None
) -> list[tuple[str, list[Path]]]:
    """Sorted (name, [6 paths]) for cubes in range [lo, hi]."""
    cube_dirs = sorted(d for d in root.iterdir() if d.is_dir() and d.name.startswith("cube_"))
    if not cube_dirs:
        sys.exit(f"ERROR: no cube_* directories in {root}")

    result = []
    for d in cube_dirs:
        num = cube_number(d.name)
        if num is not None and ((lo is not None and num < lo) or (hi is not None and num > hi)):
            continue

        imgs = {
            p.name.split("_")[0]: p
            for p in d.iterdir()
            if p.suffix.lower() in IMAGE_EXTS and p.name[0].isdigit()
        }
        missing = [i for i in range(1, FACES_PER_CUBE + 1) if str(i) not in imgs]
        if missing:
            print(f"  WARNING: {d.name} - missing photos number {missing}, SKIPPING")
            continue
        result.append((d.name, [imgs[str(i)] for i in range(1, FACES_PER_CUBE + 1)]))
    return result


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawTextHelpFormatter
    )
    ap.add_argument("root", help="directory with cube_NNNN/ subdirectories")
    ap.add_argument("--host", required=True, help="e.g. http://localhost:8080")
    ap.add_argument("--user", required=True, help="CVAT login")
    ap.add_argument(
        "--project-id",
        type=int,
        default=None,
        help="if given, tasks go to the project and inherit its labels",
    )
    ap.add_argument(
        "--from", dest="lo", type=int, default=None, help="lowest cube number (inclusive)"
    )
    ap.add_argument(
        "--to", dest="hi", type=int, default=None, help="highest cube number (inclusive)"
    )
    ap.add_argument("--dry-run", action="store_true", help="only print the plan")
    args = ap.parse_args()

    if args.lo is not None and args.hi is not None and args.lo > args.hi:
        sys.exit(f"ERROR: --from ({args.lo}) > --to ({args.hi})")

    root = Path(args.root).expanduser().resolve()
    cubes = find_cube_dirs(root, args.lo, args.hi)

    rng = ""
    if args.lo is not None or args.hi is not None:
        rng = f" (range: from={args.lo}, to={args.hi})"
    print(f"\nCandidates to create{rng}: {len(cubes)}")
    for name, imgs in cubes:
        print(f"  {name}: {[p.name for p in imgs]}")

    if not cubes:
        print("\nNo cubes match the criteria. Done.")
        return

    # SDK imported here, so --dry-run works without it (then without the duplicate check,
    # which needs the server)
    try:
        from cvat_sdk import make_client  # pyright: ignore[reportMissingImports]
        from cvat_sdk.api_client import models  # pyright: ignore[reportMissingImports]
        from cvat_sdk.core.proxies.tasks import (  # pyright: ignore[reportMissingImports]
            ResourceType,
        )
    except ImportError:
        if args.dry_run:
            print("\n(DRY-RUN without cvat-sdk: skipping the check of existing tasks.)")
            print("DRY-RUN: nothing created.")
            return
        sys.exit("ERROR: cvat-sdk missing, run with `uv run --group cvat`")

    password = getpass.getpass(f"CVAT password for {args.user}: ")

    created, failed, skipped = [], [], []
    with make_client(host=args.host) as client:
        client.login((args.user, password))

        existing = {
            t.name
            for t in client.tasks.list()
            if args.project_id is None or getattr(t, "project_id", None) == args.project_id
        }
        scope = f"in project {args.project_id}" if args.project_id else "globally"
        print(f"\n{len(existing)} tasks already exist on the server ({scope}).")

        todo = []
        for name, imgs in cubes:
            if name in existing:
                print(f"  SKIPPING {name} - a task with this name already exists")
                skipped.append(name)
            else:
                todo.append((name, imgs))

        print(f"\nTo create after filtering duplicates: {len(todo)}")

        if args.dry_run:
            for name, _ in todo:
                print(f"  (dry-run) would create: {name}")
            print("\nDRY-RUN: nothing created.")
            return

        # 7 labels: face + 6 colors with a position attribute
        position = models.AttributeRequest(
            name="position",
            mutable=False,
            input_type=models.InputTypeEnum("select"),
            values=POSITIONS,
            default_value="unset",
        )
        labels = [
            models.PatchedLabelRequest(name="face", attributes=[]),
            *(models.PatchedLabelRequest(name=c, attributes=[position]) for c in FACES),
        ]

        for name, imgs in todo:
            if args.project_id is not None:
                spec = models.TaskWriteRequest(name=name, project_id=args.project_id)
            else:
                spec = models.TaskWriteRequest(name=name, labels=labels)
            try:
                task = client.tasks.create_from_data(
                    # generated SDK models have an untyped __new__
                    spec=spec,  # pyright: ignore[reportArgumentType]
                    resource_type=ResourceType.LOCAL,
                    resources=[str(p) for p in imgs],
                )
                print(f"  OK  {name} -> task id={task.id}")
                created.append(name)
            except Exception as e:  # noqa: BLE001 - keep creating the remaining tasks
                print(f"  ERROR {name}: {e}")
                failed.append(name)

    print(
        f"\nCreated: {len(created)} | Skipped (duplicates): {len(skipped)} | Errors: {len(failed)}"
    )
    if failed:
        print("Failed:", ", ".join(failed))


if __name__ == "__main__":
    main()
