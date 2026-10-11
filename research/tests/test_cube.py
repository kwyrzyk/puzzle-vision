from collections import Counter

import pytest

from puzzle_vision.cube import (
    CENTER_IDX,
    FACELETS,
    FACELETS_PER_FACE,
    NORMAL,
    SOLVED,
    UNSET,
    CubeState,
    Face,
    Facelet,
)

# Kociemba's corner and edge facelets, e.g. URF = U9, R1, F3
CORNERS = [
    "U7 F1 L3", "U9 R1 F3", "U3 B1 R3", "U1 L1 B3", # top layer
    "D7 B9 L7", "D9 R9 B7", "D3 F9 R7", "D1 L9 F7"  # bottom layer
]  # fmt: skip
EDGES = [
    "U8 F2", "U6 R2", "U2 B2", "U4 L2", # top layer
    "F6 R4", "R6 B4", "B6 L4", "L6 F4", # middle layer
    "D8 B8", "D6 R8", "D2 F8", "D4 L8"  # bottom layer
]  # fmt: skip


def facelet(name: str) -> Facelet:
    return FACELETS[Face[name[0]] * FACELETS_PER_FACE + int(name[1]) - 1]


def test_facelet_order() -> None:
    assert len(FACELETS) == 54
    assert [(f.face, f.index) for f in FACELETS] == [
        (face, i) for face in Face for i in range(FACELETS_PER_FACE)
    ]


def test_facelets_lie_on_their_face() -> None:
    for f in FACELETS:
        normal = NORMAL[f.face]
        assert sum(p * n for p, n in zip(f.position, normal, strict=True)) == 1
        assert all(p in (-1, 0, 1) for p in f.position)


def test_center_lie_on_face_normals() -> None:
    for face in Face:
        assert FACELETS[face * FACELETS_PER_FACE + CENTER_IDX].position == NORMAL[face]


def test_cubie_types_counts() -> None:
    stickers_per_cubie = Counter(Counter(f.position for f in FACELETS).values())
    assert stickers_per_cubie == {1: 6, 2: 12, 3: 8}


@pytest.mark.parametrize("names", CORNERS + EDGES)
def test_kociemba_pieces_share_cubie(names: str) -> None:
    positions = {facelet(name).position for name in names.split()}
    assert len(positions) == 1


def test_solved() -> None:
    assert SOLVED == "U" * 9 + "R" * 9 + "F" * 9 + "D" * 9 + "L" * 9 + "B" * 9


AFTER_R_MOVE = "UUFUUFUUFRRRRRRRRRFFDFFDFFDDDBDDBDDBLLLLLLLLLUBBUBBUBB"


def test_empty_state() -> None:
    state = CubeState()
    assert str(state) == UNSET * len(FACELETS)
    assert not state.is_complete()
    assert state.color(Face.U, 0) is None


def test_set_colors_one_by_one() -> None:
    state = CubeState()
    for f, letter in zip(FACELETS, AFTER_R_MOVE, strict=True):
        assert not state.is_complete()
        state.set_color(f.face, f.index, Face[letter])
        assert state.color(f.face, f.index) is Face[letter]
    assert state.is_complete()
    assert state == CubeState.from_facelets(AFTER_R_MOVE)


@pytest.mark.parametrize("facelets", [SOLVED, AFTER_R_MOVE, UNSET * 9 + AFTER_R_MOVE[9:]])
def test_facelets_roundtrip(facelets: str) -> None:
    assert str(CubeState.from_facelets(facelets)) == facelets


@pytest.mark.parametrize("facelets", [SOLVED[:-1], SOLVED + "U", "X" + SOLVED[1:]])
def test_invalid_facelets(facelets: str) -> None:
    with pytest.raises(ValueError):
        CubeState.from_facelets(facelets)


def test_invalid_index() -> None:
    with pytest.raises(ValueError):
        CubeState().set_color(Face.U, FACELETS_PER_FACE, Face.U)
