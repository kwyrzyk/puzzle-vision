"""Rubik's cube model.

Facelets are ordered as in Kociemba's facelet string: faces U, R, F, D, L, B, nine
facelets each, numbered row by row as seen on the cube net:

                U1 U2 U3
                U4 U5 U6
                U7 U8 U9
       L1 L2 L3 F1 F2 F3 R1 R2 R3 B1 B2 B3
       L4 L5 L6 F4 F5 F6 R4 R5 R6 B4 B5 B6
       L7 L8 L9 F7 F8 F9 R7 R8 R9 B7 B8 B9
                D1 D2 D3
                D4 D5 D6
                D7 D8 D9

Geometry uses cubie coordinates in {-1, 0, 1}^3: x points to R, y to U, z to F.
"""

from enum import IntEnum
from typing import NamedTuple, Self

import numpy as np

# Integer 3D vector (x -> R, y -> U, z -> F): cubie position or face normal
type Vector = tuple[int, int, int]
type Facelets = str  # URFDLB letters, UNSET for a facelet with unknown color


class Face(IntEnum):
    U = 0
    R = 1
    F = 2
    D = 3
    L = 4
    B = 5


NORMAL: dict[Face, Vector] = {
    Face.U: (0, 1, 0),
    Face.R: (1, 0, 0),
    Face.F: (0, 0, 1),
    Face.D: (0, -1, 0),
    Face.L: (-1, 0, 0),
    Face.B: (0, 0, -1),
}

# Face adjacent to the top edge of each face on Kociemba's net; defines facelet numbering
TOP: dict[Face, Face] = {
    Face.U: Face.B,
    Face.R: Face.U,
    Face.F: Face.U,
    Face.D: Face.F,
    Face.L: Face.U,
    Face.B: Face.U,
}

# WCA scrambling orientation: white up (U), green front (F)
COLOR: dict[Face, str] = {
    Face.U: "white",
    Face.R: "red",
    Face.F: "green",
    Face.D: "yellow",
    Face.L: "orange",
    Face.B: "blue",
}

CENTER_IDX = 4
CUBE_SIZE = 3
FACELETS_PER_FACE = CUBE_SIZE**2
UNSET = "-"


class Facelet(NamedTuple):
    """One fixed position on face, not associated with color"""

    face: Face
    index: int  # 0..8, row-major on the net
    position: Vector  # cubie the facelet belongs to


def cross(a: Vector, b: Vector) -> Vector:
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _facelet(face: Face, index: int) -> Facelet:
    normal, up = NORMAL[face], NORMAL[TOP[face]]
    right = cross(up, normal)
    row, col = divmod(index, CUBE_SIZE)
    dx, dy = col - 1, 1 - row
    position = (
        normal[0] + dx * right[0] + dy * up[0],
        normal[1] + dx * right[1] + dy * up[1],
        normal[2] + dx * right[2] + dy * up[2],
    )
    return Facelet(face, index, position)


FACELETS: tuple[Facelet, ...] = tuple(
    _facelet(face, i) for face in Face for i in range(FACELETS_PER_FACE)
)

SOLVED: Facelets = "".join(face.name * FACELETS_PER_FACE for face in Face)


class CubeState:
    """Colors of the 54 facelets; any of them may not be assigned yet.

    Color is given as the face whose center has it (Face.U = white, ...).
    Colors can be set one by one.
    """

    __hash__ = None  # type: ignore[assignment]

    def __init__(self) -> None:
        self._colors = np.full(len(FACELETS), -1, dtype=np.int8)  # -1 = unknown color

    @classmethod
    def from_facelets(cls, facelets: Facelets) -> Self:
        if len(facelets) != len(FACELETS):
            raise ValueError(f"expected {len(FACELETS)} facelets, got {len(facelets)}")
        state = cls()
        for i, letter in enumerate(facelets):
            if letter == UNSET:
                continue
            if letter not in Face.__members__:
                raise ValueError(f"invalid facelet {letter!r} at {i}")
            state._colors[i] = Face[letter]
        return state

    def __str__(self) -> Facelets:
        return "".join(UNSET if color < 0 else Face(color).name for color in self._colors)

    def __eq__(self, other: object) -> bool:
        return isinstance(other, CubeState) and np.array_equal(self._colors, other._colors)

    def set_color(self, face: Face, index: int, color: Face) -> None:
        self._colors[_facelet_id(face, index)] = color

    def color(self, face: Face, index: int) -> Face | None:
        color = self._colors[_facelet_id(face, index)]
        return None if color < 0 else Face(color)

    def is_complete(self) -> bool:
        return bool(np.all(self._colors >= 0))


def _facelet_id(face: Face, index: int) -> int:
    if not 0 <= index < FACELETS_PER_FACE:
        raise ValueError(f"facelet index out of range: {index}")
    return face * FACELETS_PER_FACE + index
