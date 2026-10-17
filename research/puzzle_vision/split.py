"""Train/val/test split of cubes.

The split is by cube, not by photo: the 6 photos of one cube are similar (same cube, light,
background), so splitting photos would leak them between the sets. Whole cubes in a split
also allow validating the state of the whole cube.
"""

import math
import random


def split_cubes(
    cube_ids: list[str], train_size: float, val_size: float = 0, *, seed: int | None = None
) -> dict[str, str]:
    """Split name ('train', 'val', 'test') of each cube.

    train_size, val_size: fractions of cubes, the rest goes to test; an empty split is left out
    seed: the same cube ids and seed give the same split; random without a seed
    """
    total = train_size + val_size
    if min(train_size, val_size) < 0 or (total > 1 and not math.isclose(total, 1)):
        raise ValueError(
            f"sizes must be non-negative, at most 1 in total: {train_size}, {val_size}"
        )
    shuffled = sorted(cube_ids)
    random.Random(seed).shuffle(shuffled)

    train_end = round(train_size * len(shuffled))
    val_end = round(total * len(shuffled))
    splits = {
        "train": shuffled[:train_end],
        "val": shuffled[train_end:val_end],
        "test": shuffled[val_end:],
    }
    return {cube_id: name for name, cubes in splits.items() for cube_id in cubes}
