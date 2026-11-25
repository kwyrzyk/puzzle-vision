import csv
import itertools
import re

import pytest

from puzzle_vision.config import (
    PUZZLE_3X3,
    PUZZLE_CLOCK,
    PUZZLE_TYPES,
    RAW_IMAGES_DIR,
    RAW_METADATA_DIR,
    raw_images_dir,
    raw_metadata,
    raw_videos_dir,
)
from puzzle_vision.cube import parse_moves

# photo files of a sample, per puzzle type
FILES = {
    PUZZLE_3X3: [
        "1_white.jpg",
        "2_green.jpg",
        "3_orange.jpg",
        "4_blue.jpg",
        "5_red.jpg",
        "6_yellow.jpg",
    ],
    PUZZLE_CLOCK: ["1_front.jpg", "2_back.jpg"],
}
CUBE_ID = re.compile(r"cube_\d{4}")
CLOCK_MOVE = re.compile(r"(UR|DR|DL|UL|U|R|D|L|ALL)[0-6][+-]")
PINS = {"UR", "DR", "DL", "UL"}


def read_csv(name: str) -> list[dict[str, str]]:
    with (RAW_METADATA_DIR / name).open(newline="") as f:
        return list(csv.DictReader(f))


def samples(puzzle: str) -> list[dict[str, str]]:
    with raw_metadata(puzzle).open(newline="") as f:
        return list(csv.DictReader(f))


@pytest.mark.parametrize(
    ("name", "key"),
    [("3x3.csv", "cube_id"), ("puzzles.csv", "puzzle_id"), ("cameras.csv", "camera_id")],
)
def test_ids_are_unique(name: str, key: str) -> None:
    ids = [row[key] for row in read_csv(name)]
    assert ids
    assert len(ids) == len(set(ids))


def test_one_samples_table_per_puzzle_type() -> None:
    types = {row["type"] for row in read_csv("puzzles.csv")}
    assert types <= set(PUZZLE_TYPES)
    for puzzle in PUZZLE_TYPES:
        assert raw_metadata(puzzle).is_file(), puzzle


@pytest.mark.parametrize("puzzle", PUZZLE_TYPES)
def test_sample_ids(puzzle: str) -> None:
    ids = [row["cube_id"] for row in samples(puzzle)]
    assert len(ids) == len(set(ids))
    bad = [cube_id for cube_id in ids if not CUBE_ID.fullmatch(cube_id)]
    assert not bad


@pytest.mark.parametrize("puzzle", PUZZLE_TYPES)
def test_foreign_keys_exist(puzzle: str) -> None:
    # the puzzle of a sample is of the type of its table
    types = {row["puzzle_id"]: row["type"] for row in read_csv("puzzles.csv")}
    cameras = {row["camera_id"] for row in read_csv("cameras.csv")}
    for row in samples(puzzle):
        assert types.get(row["puzzle_id"]) == puzzle, row["cube_id"]
        assert row["camera_id"] in cameras, row["cube_id"]


def test_cube_scrambles_are_in_valid_wca_notation() -> None:
    for row in samples(PUZZLE_3X3):
        moves = parse_moves(row["scramble"])  # ValueError on an invalid move
        assert moves, row["cube_id"]

        # no same face move twice in a row
        repeated = [a for a, b in itertools.pairwise(moves) if a[0] == b[0]]
        assert not repeated, f"{row['cube_id']}: same face repeated after {repeated}"


def test_clock_scrambles_are_in_valid_wca_notation() -> None:
    for row in samples(PUZZLE_CLOCK):
        tokens = row["scramble"].split()
        assert tokens.count("y2") == 1, row["cube_id"]
        moves = [t for t in tokens if t != "y2"]
        # final pin positions of older scrambles may follow the moves
        while moves and moves[-1] in PINS:
            moves.pop()
        bad = [t for t in moves if not CLOCK_MOVE.fullmatch(t)]
        assert not bad, f"{row['cube_id']}: {bad}"


def test_videos_belong_to_samples() -> None:
    cube_ids = {row["cube_id"] for row in samples(PUZZLE_3X3)}
    for row in read_csv("3x3_videos.csv"):
        assert row["cube_id"] in cube_ids, row


requires_images = pytest.mark.skipif(
    not RAW_IMAGES_DIR.is_dir(), reason="raw images not pulled (run `dvc pull`)"
)


@requires_images
def test_only_puzzle_type_folders() -> None:
    assert {p.name for p in RAW_IMAGES_DIR.iterdir()} <= set(PUZZLE_TYPES)


@requires_images
@pytest.mark.parametrize("puzzle", PUZZLE_TYPES)
def test_every_sample_has_folder_with_its_photos(puzzle: str) -> None:
    folder = raw_images_dir(puzzle)
    folders = {p.name for p in folder.iterdir()} if folder.is_dir() else set()
    assert folders == {row["cube_id"] for row in samples(puzzle)}
    for cube_id in folders:
        files = sorted(p.name for p in (folder / cube_id).iterdir())
        assert files == FILES[puzzle], cube_id


@pytest.mark.skipif(not raw_videos_dir(PUZZLE_3X3).is_dir(), reason="raw videos not pulled")
def test_every_video_exists() -> None:
    for row in read_csv("3x3_videos.csv"):
        assert (
            raw_videos_dir(PUZZLE_3X3) / row["cube_id"] / f"{row['video_id']}.mp4"
        ).is_file(), row
