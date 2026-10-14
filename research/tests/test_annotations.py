import math
from pathlib import Path
import random

import pytest

from puzzle_vision.annotations import (
    PHOTO_TOP,
    Point,
    Sticker,
    grid_order,
    parse_cvat,
    set_face_colors,
)
from puzzle_vision.cube import FACELETS_PER_FACE, UNSET, CubeState, Face, face_view

FIXTURE = Path(__file__).parent / "fixtures" / "cvat_annotations.xml"


def test_parse_cvat() -> None:
    photo, empty = parse_cvat(FIXTURE)
    assert (photo.cube_id, photo.face, len(photo.stickers)) == ("cube_0001", Face.U, 9)
    assert photo.stickers[0] == Sticker(Face.F, [(200, 100), (300, 100), (300, 200), (200, 200)])
    assert photo.outlines == [[(0, 0), (300, 0), (300, 300), (0, 300)]]
    assert (empty.cube_id, empty.face, empty.stickers) == ("cube_0001", Face.F, [])
    assert empty.outlines == []


def test_sticker_center() -> None:
    assert Sticker(Face.U, [(0, 0), (4, 0), (4, 2), (0, 2)]).center == (2, 1)


def skewed_grid(angle: float) -> list[Point]:
    """3x3 sticker centers rotated by angle (radians), lower rows wider (perspective)"""
    cos, sin = math.cos(angle), math.sin(angle)
    points = []
    for row in range(3):
        for col in range(3):
            x, y = (col - 1) * (100 + 10 * row), (row - 1) * 100
            points.append((x * cos - y * sin, x * sin + y * cos))
    return points


@pytest.mark.parametrize("angle", [0.0, 0.2, -0.2])
def test_grid_order(angle: float) -> None:
    points = skewed_grid(angle)
    shuffled = list(range(9))
    random.Random(0).shuffle(shuffled)
    order = grid_order([points[i] for i in shuffled])
    assert [shuffled[i] for i in order] == list(range(9))


@pytest.mark.parametrize("count", [0, 8, 10])
def test_grid_needs_nine_stickers(count: int) -> None:
    with pytest.raises(ValueError):
        grid_order((skewed_grid(0) * 2)[:count])


def test_face_view() -> None:
    # all photos have the same top face as the net, except D
    for face in Face:
        if face is not Face.D:
            assert face_view(face, PHOTO_TOP[face]) == list(range(FACELETS_PER_FACE))
    # D seen with R at the top: net rotated counterclockwise, D3 in the top left corner
    assert face_view(Face.D, Face.R) == [2, 5, 8, 1, 4, 7, 0, 3, 6]


def test_set_face_colors() -> None:
    state = CubeState()
    set_face_colors(state, parse_cvat(FIXTURE)[0])
    after_r = str(CubeState.from_scramble("R"))
    assert str(state) == after_r[:FACELETS_PER_FACE] + UNSET * 45


def test_set_face_colors_needs_nine_stickers() -> None:
    with pytest.raises(ValueError):
        set_face_colors(CubeState(), parse_cvat(FIXTURE)[1])
