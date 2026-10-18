"""Raw data: CVAT annotations agree with the scrambles in cubes.csv."""

import csv
from pathlib import Path

from PIL import Image
import pytest

from puzzle_vision.annotations import ImageAnnotation, Sticker, parse_cvat, set_face_colors
from puzzle_vision.config import RAW_ANNOTATIONS_DIR, RAW_IMAGES_DIR, RAW_METADATA_DIR
from puzzle_vision.cube import COLOR, FACELETS_PER_FACE, CubeState, Face
from puzzle_vision.export import CLASSES, shapes
from puzzle_vision.export.yolo import read_labels, write_labels

ANNOTATIONS = RAW_ANNOTATIONS_DIR / "cvat_annotations.xml"
EXIF_ORIENTATION = 0x0112
TRANSPOSED = {5, 6, 7, 8}  # EXIF orientations that swap width and height
FACE_OF_CLASS = {CLASSES.index(color): face for face, color in COLOR.items()}

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


def test_images_match_annotated_size(images: dict[str, list[ImageAnnotation]]) -> None:
    for image in (image for cube in images.values() for image in cube):
        with Image.open(RAW_IMAGES_DIR / image.cube_id / image.file_name) as photo:
            width, height = photo.size
            if photo.getexif().get(EXIF_ORIENTATION) in TRANSPOSED:
                width, height = height, width
        assert (width, height) == image.size, (image.cube_id, image.file_name)


def test_yolo_labels_reproduce_scramble(
    images: dict[str, list[ImageAnnotation]], tmp_path: Path
) -> None:
    """Colors read back from written YOLO labels give the scrambled cube"""
    for cube_id in SCRAMBLES.keys() - KNOWN_ERRORS.keys():
        state = CubeState()
        for image in images[cube_id]:
            path = tmp_path / f"{cube_id}_{image.file_name}.txt"
            write_labels(path, shapes(image), image.size)
            stickers = [
                Sticker(FACE_OF_CLASS[class_id], polygon)
                for class_id, polygon in read_labels(path, image.size)
                if class_id in FACE_OF_CLASS
            ]
            set_face_colors(state, image._replace(stickers=stickers))
        assert state == CubeState.from_scramble(SCRAMBLES[cube_id]), cube_id
