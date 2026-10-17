"""Raw data: CVAT annotations agree with the scrambles in cubes.csv."""

import csv

import pytest

from puzzle_vision.annotations import ImageAnnotation, parse_cvat, set_face_colors
from puzzle_vision.config import RAW_ANNOTATIONS_DIR, RAW_METADATA_DIR
from puzzle_vision.cube import FACELETS_PER_FACE, CubeState, Face

ANNOTATIONS = RAW_ANNOTATIONS_DIR / "cvat_annotations.xml"

with (RAW_METADATA_DIR / "cubes.csv").open(newline="") as f:
    SCRAMBLES = {row["cube_id"]: row["scramble"] for row in csv.DictReader(f)}

# Errors found in the data, to be fixed in CVAT; strict xfail fails once fixed
KNOWN_ERRORS = {
    **{f"cube_{n:04}": "not annotated" for n in range(21, 26)},
}

pytestmark = pytest.mark.skipif(
    not ANNOTATIONS.exists(), reason="annotations not pulled (run `dvc pull`)"
)


@pytest.fixture(scope="module")
def images() -> dict[str, list[ImageAnnotation]]:
    by_cube: dict[str, list[ImageAnnotation]] = {cube_id: [] for cube_id in SCRAMBLES}
    for image in parse_cvat(ANNOTATIONS):
        by_cube[image.cube_id].append(image)
    return by_cube


@pytest.mark.parametrize(
    "cube_id",
    [
        pytest.param(cube_id, marks=pytest.mark.xfail(reason=KNOWN_ERRORS[cube_id], strict=True))
        if cube_id in KNOWN_ERRORS
        else cube_id
        for cube_id in SCRAMBLES
    ],
)
def test_annotations_match_scramble(
    images: dict[str, list[ImageAnnotation]], cube_id: str
) -> None:
    assert sorted(image.face for image in images[cube_id]) == list(Face)
    state, problems = CubeState(), []
    for image in images[cube_id]:
        if len(image.outlines) != 1:
            problems.append(f"{image.face.name}: {len(image.outlines)} outlines")
        if len(image.stickers) == FACELETS_PER_FACE:
            set_face_colors(state, image)
        else:
            problems.append(f"{image.face.name}: {len(image.stickers)} stickers")
    differences = state.differences(CubeState.from_scramble(SCRAMBLES[cube_id]))
    names = [f"{f.face.name}{f.index + 1}" for f in differences]
    assert not names, (names, state.defects())
    assert not problems
