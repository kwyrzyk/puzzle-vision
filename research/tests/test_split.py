from collections import Counter

import pytest

from puzzle_vision.split import split_cubes

CUBES = [f"cube_{n:04}" for n in range(1, 21)]


@pytest.mark.parametrize(
    ("train_size", "val_size", "expected"),
    [
        (0.0, 0.0, {"test": 20}),
        (1.0, 0.0, {"train": 20}),
        (0.0, 1.0, {"val": 20}),
        (0.5, 0.5, {"train": 10, "val": 10}),
        (0.4, 0.4, {"train": 8, "val": 8, "test": 4}),
        (0.8, 0.2, {"train": 16, "val": 4}),
        (0.33, 0.33, {"train": 7, "val": 6, "test": 7}),
        (0.68, 0.16, {"train": 14, "val": 3, "test": 3}),
    ],
)
def test_split_size(train_size: float, val_size: float, expected: dict[str, int]) -> None:
    split_of = split_cubes(CUBES, train_size=train_size, val_size=val_size)
    assert split_of.keys() == set(CUBES)
    assert Counter(split_of.values()) == expected


@pytest.mark.parametrize(
    ("train_size", "expected"),
    [
        (0.0, {"test": 20}),
        (0.5, {"train": 10, "test": 10}),
        (1.0, {"train": 20}),
    ],
)
def test_split_without_val(train_size: float, expected: dict[str, int]) -> None:
    split_of = split_cubes(CUBES, train_size=train_size)
    assert split_of.keys() == set(CUBES)
    assert "val" not in split_of.values()
    assert Counter(split_of.values()) == expected


def test_split_is_deterministic() -> None:
    split_of = split_cubes(CUBES, 0.6, 0.2, seed=0)
    assert split_of == split_cubes(CUBES[::-1], 0.6, 0.2, seed=0)
    assert split_of != split_cubes(CUBES, 0.6, 0.2, seed=1)


def test_split_without_seed_is_random() -> None:
    splits = [split_cubes(CUBES, 0.6, 0.2) for _ in range(5)]
    assert any(split_of != splits[0] for split_of in splits)  # all equal by chance: ~1e-28


@pytest.mark.parametrize(
    ("train_size", "val_size"),
    [
        (1.2, 0.0),
        (0.6, -0.1),
        (-0.3, 0.8),
        (0.3, 0.8),
    ],
)
def test_invalid_split_size(train_size: float, val_size: float) -> None:
    with pytest.raises(ValueError):
        split_cubes(CUBES, train_size=train_size, val_size=val_size)
