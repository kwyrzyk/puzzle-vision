"""Export of annotated photos to training dataset formats.

A new format: subclass DatasetWriter in a new module, decorate it with @register("name")
and import the module below.
"""

from pathlib import Path

from puzzle_vision.annotations import ImageAnnotation
from puzzle_vision.export import yolo  # noqa: F401 - registers the writer
from puzzle_vision.export.base import CLASSES, WRITERS, DatasetWriter, Shape, register, shapes

__all__ = ["CLASSES", "WRITERS", "DatasetWriter", "Shape", "export", "register", "shapes"]


def export(
    images: list[ImageAnnotation],
    split_of: dict[str, str],
    writer: DatasetWriter,
    images_dir: Path,
) -> None:
    """Write every image to the split of its cube; images_dir: folders cube_NNNN/

    Splits are written in the order of split_of values, e.g. train, val, test.
    """
    order = list(dict.fromkeys(split_of.values()))
    for image in sorted(images, key=lambda image: order.index(split_of[image.cube_id])):
        writer.add(split_of[image.cube_id], image, images_dir / image.cube_id / image.file_name)
    writer.close()
