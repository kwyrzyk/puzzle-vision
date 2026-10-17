from collections import Counter
import itertools
import random

import pytest

from puzzle_vision.cube import (
    CENTER_IDX,
    CORNERS,
    EDGES,
    FACELETS,
    FACELETS_PER_FACE,
    NORMAL,
    SOLVED,
    UNSET,
    CubeState,
    Defect,
    Face,
    Facelet,
    parse_moves,
)

# Kociemba's corner and edge facelets, e.g. URF = U9, R1, F3
CORNER_FACELETS = {
    "URF": "U9 R1 F3", "UFL": "U7 F1 L3", "ULB": "U1 L1 B3", "UBR": "U3 B1 R3",
    "DFR": "D3 F9 R7", "DLF": "D1 L9 F7", "DBL": "D7 B9 L7", "DRB": "D9 R9 B7",
}  # fmt: skip
EDGE_FACELETS = {
    "UR": "U6 R2", "UF": "U8 F2", "UL": "U4 L2", "UB": "U2 B2",
    "DR": "D6 R8", "DF": "D2 F8", "DL": "D4 L8", "DB": "D8 B8",
    "FR": "F6 R4", "FL": "F4 L6", "BL": "B6 L4", "BR": "B4 R6",
}  # fmt: skip


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


@pytest.mark.parametrize("names", [*CORNER_FACELETS.values(), *EDGE_FACELETS.values()])
def test_kociemba_pieces_share_cubie(names: str) -> None:
    positions = {facelet(name).position for name in names.split()}
    assert len(positions) == 1


def test_piece_names() -> None:
    assert list(CORNERS) == list(CORNER_FACELETS)
    assert list(EDGES) == list(EDGE_FACELETS)


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


SCRAMBLE = "B' L' U2 F R2 F2 L2 U' L' U' D' F R U' B' F R2 F' L' F' U D' F' B2 R"


# Expected states from Kociemba's twophase
@pytest.mark.parametrize(
    ("moves", "facelets"),
    [
        ("R", AFTER_R_MOVE),
        ("U", "UUUUUUUUUBBBRRRRRRRRRFFFFFFDDDDDDDDDFFFLLLLLLLLLBBBBBB"),
        ("F'", "UUUUUURRRDRRDRRDRRFFFFFFFFFLLLDDDDDDLLULLULLUBBBBBBBBB"),
    ],
)
def test_single_moves(moves: str, facelets: str) -> None:
    assert str(CubeState.from_scramble(moves)) == facelets


def test_centers_never_move() -> None:
    state = CubeState.from_scramble(SCRAMBLE)
    for face in Face:
        assert state.color(face, CENTER_IDX) is face


@pytest.mark.parametrize("face", "URFDLB")
def test_turn_identities(face: str) -> None:
    assert str(CubeState.from_scramble(f"{face} {face} {face} {face}")) == SOLVED
    assert CubeState.from_scramble(f"{face} {face}") == CubeState.from_scramble(f"{face}2")
    assert str(CubeState.from_scramble(f"{face} {face}'")) == SOLVED


def test_opposite_faces_commute() -> None:
    for a, b in ("UD", "RL", "FB"):
        assert CubeState.from_scramble(f"{a} {b}") == CubeState.from_scramble(f"{b} {a}")


@pytest.mark.parametrize(("moves", "order"), [("R U R' U'", 6), ("R U", 105)])
def test_sequence_order(moves: str, order: int) -> None:
    """Sequence repeated order times, and not fewer, returns to solved"""
    state = CubeState.from_facelets(SOLVED)
    for i in range(1, order + 1):
        state.apply(moves)
        assert (str(state) == SOLVED) == (i == order)


def test_parse_moves() -> None:
    assert parse_moves("R U2 F'") == [(Face.R, 1), (Face.U, 2), (Face.F, 3)]
    all_moves = " ".join(f + s for f, s in itertools.product("URFDLB", ["", "2", "'"]))
    assert len(parse_moves(all_moves)) == 18


@pytest.mark.parametrize("moves", ["R3", "X", "r", "R2'", "RU"])
def test_invalid_move(moves: str) -> None:
    with pytest.raises(ValueError):
        parse_moves(moves)


def test_moves_need_all_colors() -> None:
    with pytest.raises(ValueError):
        CubeState.from_facelets(UNSET + SOLVED[1:]).apply("R")


AFTER_R_U_UNKNOWN = UNSET * FACELETS_PER_FACE + AFTER_R_MOVE[FACELETS_PER_FACE:]
R_CHANGES = "U3 U6 U9 F3 F6 F9 D3 D6 D9 B1 B4 B7"


@pytest.mark.parametrize(
    ("facelets", "other", "expected"),
    [
        (AFTER_R_MOVE, AFTER_R_MOVE, ""),
        (AFTER_R_MOVE, SOLVED, R_CHANGES),
        (AFTER_R_U_UNKNOWN, AFTER_R_MOVE, ""),
        (AFTER_R_U_UNKNOWN, SOLVED, R_CHANGES.removeprefix("U3 U6 U9 ")),
        (SOLVED, AFTER_R_U_UNKNOWN, R_CHANGES.removeprefix("U3 U6 U9 ")),
    ],
)
def test_differences(facelets: str, other: str, expected: str) -> None:
    state = CubeState.from_facelets(facelets)
    differences = state.differences(CubeState.from_facelets(other))
    assert [f"{f.face.name}{f.index + 1}" for f in differences] == expected.split()


def edit_facelets(facelets: str, names: str, colors: str) -> str:
    """Set colors of the named facelets, e.g. edit(SOLVED, "U6 R2", "RU")"""
    chars = list(facelets)
    for name, color in zip(names.split(), colors, strict=True):
        f = facelet(name)
        chars[f.face * FACELETS_PER_FACE + f.index] = color
    return "".join(chars)


def blank_facelets(facelets: str, names: str) -> str:
    return edit_facelets(facelets, names, UNSET * len(names.split()))


SCRAMBLED = str(CubeState.from_scramble(SCRAMBLE))
ALL_CORNERS = " ".join(CORNER_FACELETS.values())
ALL_EDGES = " ".join(EDGE_FACELETS.values())


@pytest.mark.parametrize(
    ("facelets", "expected"),
    [
        (UNSET * len(FACELETS), []),
        (SOLVED, []),
        (SCRAMBLED, []),
        (blank_facelets(SCRAMBLED, ALL_EDGES), []),
        (edit_facelets(UNSET * len(FACELETS), "U5", "D"), [Defect.CENTERS]),
        # impossible colors: U and D on one piece
        (edit_facelets(SOLVED, "U9", "D"), [Defect.CORNERS]),
        (edit_facelets(SOLVED, "U6", "D"), [Defect.EDGES]),
        # mirrored corner: colors of URF counterclockwise
        (edit_facelets(SOLVED, "U9 R1 F3", "UFR"), [Defect.CORNERS]),
        # each place fits, but both need the same piece URF
        (edit_facelets(UNSET * len(FACELETS), "U9 R1 U7 F1", "URUR"), [Defect.CORNERS]),
        (
            edit_facelets(
                UNSET * len(FACELETS), "U8 U4 U2 D8 F8 D6 R8 F6 R4 B4 R6", "UUUURDRFRBR"
            ),
            [Defect.EDGES],
        ),
        (edit_facelets(SOLVED, "U9 R1 F3", "FUR"), [Defect.CORNER_TWIST]),
        (edit_facelets(SOLVED, "U6 R2", "RU"), [Defect.EDGE_FLIP]),
        # swapped edges UR and UF
        (edit_facelets(SOLVED, "U6 R2 U8 F2", "UFUR"), [Defect.PARITY]),
        # swapped corners URF and UFL, and edges UR and UF: solvable
        (edit_facelets(SOLVED, "U9 R1 F3 U7 F1 L3 U6 R2 U8 F2", "UFLURFUFUR"), []),
    ],
)
def test_defects(facelets: str, expected: list[Defect]) -> None:
    assert CubeState.from_facelets(facelets).defects() == expected


def test_twist_needs_two_colors_of_each_corner() -> None:
    twisted = edit_facelets(SOLVED, "U9 R1 F3", "FUR")
    second, third = (
        " ".join(names.split()[k] for names in CORNER_FACELETS.values()) for k in (1, 2)
    )
    two_colors = blank_facelets(twisted, third)
    assert CubeState.from_facelets(two_colors).defects() == [Defect.CORNER_TWIST]
    one_color = blank_facelets(two_colors, second)
    assert CubeState.from_facelets(one_color).defects() == []


def test_parity_needs_both_groups() -> None:
    swapped_edges = edit_facelets(SOLVED, "U6 R2 U8 F2", "UFUR")
    assert CubeState.from_facelets(blank_facelets(swapped_edges, ALL_CORNERS)).defects() == []
    # one color of edge DF: DF or FD can't be told
    assert CubeState.from_facelets(blank_facelets(swapped_edges, "D2")).defects() == []


@pytest.mark.parametrize("seed", range(20))
def test_no_defects_on_partial_state(seed: int) -> None:
    rng = random.Random(seed)
    facelets = "".join(c if rng.random() < 0.6 else UNSET for c in SCRAMBLED)
    assert CubeState.from_facelets(facelets).defects() == []
