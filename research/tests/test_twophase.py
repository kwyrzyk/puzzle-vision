"""Cross-check with Kociemba's RubikTwoPhase (GPL-3.0, used only in tests)."""

import random

import pytest
from twophase import cubie, face

from puzzle_vision.cube import CENTER_IDX, FACELETS, FACELETS_PER_FACE, CubeState, Defect

# twophase.cubie.CubieCube.verify() messages; it reports only the first defect found
ERRORS = {
    "Error: Some edges are undefined.": Defect.EDGES,
    "Error: Total edge flip is wrong.": Defect.EDGE_FLIP,
    "Error: Some corners are undefined.": Defect.CORNERS,
    "Error: Total corner twist is wrong.": Defect.CORNER_TWIST,
    "Error: Wrong edge and corner parity": Defect.PARITY,  # sic, no period
}


def random_scramble(rng: random.Random, length: int = 25) -> str:
    return " ".join(rng.choice("URFDLB") + rng.choice(["", "2", "'"]) for _ in range(length))


def twophase_cube(scramble: str) -> cubie.CubieCube:
    cube = cubie.CubieCube()
    for move in scramble.split():
        turns = {"": 1, "2": 2, "'": 3}[move[1:]]
        for _ in range(turns):
            cube.multiply(cubie.basicMoveCube["URFDLB".index(move[0])])
    return cube


def twophase_defect(facelets: str) -> Defect | None:
    face_cube = face.FaceCube()
    face_cube.from_string(facelets)
    result = face_cube.to_cubie_cube().verify()
    return None if result is cubie.CUBE_OK else ERRORS[result]


@pytest.mark.parametrize("seed", range(50))
def test_moves(seed: int) -> None:
    scramble = random_scramble(random.Random(seed))
    expected = twophase_cube(scramble).to_facelet_cube().to_string()
    assert str(CubeState.from_scramble(scramble)) == expected


@pytest.mark.parametrize("seed", range(50))
def test_corner_twist_defect(seed: int) -> None:
    rng = random.Random(seed)
    cube = twophase_cube(random_scramble(rng))

    corner = rng.randrange(8)
    cube.co[corner] = (cube.co[corner] + rng.choice([1, 2])) % 3
    facelets = cube.to_facelet_cube().to_string()

    assert twophase_defect(facelets) is Defect.CORNER_TWIST
    assert CubeState.from_facelets(facelets).defects() == [Defect.CORNER_TWIST]


@pytest.mark.parametrize("seed", range(50))
def test_edge_flip_defect(seed: int) -> None:
    rng = random.Random(seed)
    cube = twophase_cube(random_scramble(rng))

    cube.eo[rng.randrange(12)] ^= 1
    facelets = cube.to_facelet_cube().to_string()

    assert twophase_defect(facelets) is Defect.EDGE_FLIP
    assert CubeState.from_facelets(facelets).defects() == [Defect.EDGE_FLIP]


@pytest.mark.parametrize("seed", range(50))
def test_parity_defect(seed: int) -> None:
    rng = random.Random(seed)
    cube = twophase_cube(random_scramble(rng))

    a, b = rng.sample(range(12), 2)
    cube.ep[a], cube.ep[b] = cube.ep[b], cube.ep[a]
    facelets = cube.to_facelet_cube().to_string()

    assert twophase_defect(facelets) is Defect.PARITY
    assert CubeState.from_facelets(facelets).defects() == [Defect.PARITY]


@pytest.mark.parametrize("seed", range(300))
def test_swapped_facelets(seed: int) -> None:
    """Two non-center facelets of a scrambled cube swapped, like misannotated colors.

    Whatever twophase rejects, we reject too. We are stricter in one case: twophase
    recognizes a corner by two colors, so it accepts a mirrored corner.
    """
    rng = random.Random(seed)
    letters = list(str(CubeState.from_scramble(random_scramble(rng))))
    not_centers = [i for i in range(len(FACELETS)) if i % FACELETS_PER_FACE != CENTER_IDX]
    i, j = rng.sample(not_centers, 2)
    letters[i], letters[j] = letters[j], letters[i]
    facelets = "".join(letters)
    expected, found = twophase_defect(facelets), CubeState.from_facelets(facelets).defects()
    if expected is None:
        assert found in ([], [Defect.CORNERS])
    else:
        assert found  # defect may be different, twophase only reports first found defect
