import csv
import itertools
import re

import pytest

from puzzle_vision.config import RAW_IMAGES_DIR, RAW_METADATA_DIR

FACE_FILES = [
    "1_white.jpg",
    "2_green.jpg",
    "3_orange.jpg",
    "4_blue.jpg",
    "5_red.jpg",
    "6_yellow.jpg",
]
CUBE_ID = re.compile(r"cube_\d{4}")
MOVE = re.compile(r"[UDLRFB]['2]?")


def read_csv(name: str) -> list[dict[str, str]]:
    with (RAW_METADATA_DIR / name).open(newline="") as f:
        return list(csv.DictReader(f))


@pytest.fixture(scope="module")
def cubes() -> list[dict[str, str]]:
    return read_csv("cubes.csv")


@pytest.mark.parametrize(
    ("name", "key"),
    [("cubes.csv", "cube_id"), ("puzzles.csv", "puzzle_id"), ("cameras.csv", "camera_id")],
)
def test_ids_are_unique(name: str, key: str) -> None:
    ids = [row[key] for row in read_csv(name)]
    assert ids
    assert len(ids) == len(set(ids))


def test_cube_id_format(cubes: list[dict[str, str]]) -> None:
    bad = [row["cube_id"] for row in cubes if not CUBE_ID.fullmatch(row["cube_id"])]
    assert not bad


@pytest.mark.parametrize(
    ("name", "key"), [("puzzles.csv", "puzzle_id"), ("cameras.csv", "camera_id")]
)
def test_foreign_keys_exist(cubes: list[dict[str, str]], name: str, key: str) -> None:
    existing_key_values = {row[key] for row in read_csv(name)}
    used_foreign_keys = {row[key] for row in cubes}
    assert used_foreign_keys.issubset(existing_key_values)


def test_scrambles_are_in_valid_wca_notation(cubes: list[dict[str, str]]) -> None:
    for row in cubes:
        moves = row["scramble"].split()
        assert moves, row["cube_id"]
        bad = [m for m in moves if not MOVE.fullmatch(m)]
        assert not bad, f"{row['cube_id']}: invalid moves {bad}"

        # no same face move twice in a row
        repeated = [a for a, b in itertools.pairwise(moves) if a[0] == b[0]]
        assert not repeated, f"{row['cube_id']}: same face repeated after {repeated}"


requires_images = pytest.mark.skipif(
    not RAW_IMAGES_DIR.is_dir(), reason="raw images not pulled (run `dvc pull`)"
)


@requires_images
def test_every_cube_has_folder(cubes: list[dict[str, str]]) -> None:
    folders = {p.name for p in RAW_IMAGES_DIR.iterdir() if p.is_dir()}
    assert folders == {row["cube_id"] for row in cubes}


@requires_images
def test_every_cube_has_six_faces_images(cubes: list[dict[str, str]]) -> None:
    for row in cubes:
        files = sorted(p.name for p in (RAW_IMAGES_DIR / row["cube_id"]).iterdir())
        assert files == FACE_FILES, row["cube_id"]
