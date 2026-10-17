"""Interface and classes shared by all dataset formats."""

from abc import ABC, abstractmethod
from collections.abc import Callable
from pathlib import Path

from puzzle_vision.annotations import OUTLINE_LABEL, ImageAnnotation, Point
from puzzle_vision.cube import COLOR

# Convention of classes order
CLASSES = (OUTLINE_LABEL, "white", "green", "orange", "blue", "red", "yellow")

type Shape = tuple[int, list[Point]]  # class id, polygon in pixels


def shapes(image: ImageAnnotation) -> list[Shape]:
    """Outlines, then stickers, in annotation order"""
    outline = CLASSES.index(OUTLINE_LABEL)
    return [(outline, polygon) for polygon in image.outlines] + [
        (CLASSES.index(COLOR[sticker.color]), sticker.polygon) for sticker in image.stickers
    ]


class DatasetWriter(ABC):
    def __init__(self, out_dir: Path) -> None:
        self.out_dir = out_dir

    @abstractmethod
    def add(self, split: str, image: ImageAnnotation, source: Path) -> None:
        """Add the photo from file source with its annotations to the split"""

    def close(self) -> None:  # noqa: B027 - optional hook, most formats need it
        """Write files describing the whole dataset, after all photos are added"""


# Format name -> writer class, filled by @register
WRITERS: dict[str, type[DatasetWriter]] = {}


def register[W: DatasetWriter](name: str) -> Callable[[type[W]], type[W]]:
    """Class decorator adding a writer to WRITERS under the format name"""

    def add(cls: type[W]) -> type[W]:
        if name in WRITERS:
            raise ValueError(f"format {name!r} already registered by {WRITERS[name].__name__}")
        WRITERS[name] = cls
        return cls

    return add
