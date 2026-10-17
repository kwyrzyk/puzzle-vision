"""Ultralytics YOLO segmentation format, https://docs.ultralytics.com/datasets/segment/

out_dir/
    data.yaml
    images/<split>/<cube_id>_<photo>.jpg
    labels/<split>/<cube_id>_<photo>.txt  line per polygon: class x1 y1 x2 y2 ..., in 0..1
"""

from pathlib import Path
import shutil

from puzzle_vision.annotations import ImageAnnotation
from puzzle_vision.export.base import CLASSES, DatasetWriter, Shape, register, shapes


@register("yolo-seg")
class YoloSegWriter(DatasetWriter):
    def __init__(self, out_dir: Path) -> None:
        super().__init__(out_dir)
        self._splits: list[str] = []

    def add(self, split: str, image: ImageAnnotation, source: Path) -> None:
        if split not in self._splits:
            self._splits.append(split)
            for kind in ("images", "labels"):
                (self.out_dir / kind / split).mkdir(parents=True)
        name = f"{image.cube_id}_{Path(image.file_name).stem}"
        shutil.copyfile(source, self.out_dir / "images" / split / f"{name}{source.suffix}")
        write_labels(self.out_dir / "labels" / split / f"{name}.txt", shapes(image), image.size)

    def close(self) -> None:
        # no 'path' key: Ultralytics then resolves split folders relative to data.yaml
        lines = [f"{split}: images/{split}" for split in self._splits]
        lines += ["names:", *(f"  {i}: {name}" for i, name in enumerate(CLASSES))]
        (self.out_dir / "data.yaml").write_text("".join(f"{line}\n" for line in lines))


def write_labels(path: Path, image_shapes: list[Shape], size: tuple[int, int]) -> None:
    width, height = size
    lines = (
        " ".join([str(class_id), *(f"{x / width:.6f} {y / height:.6f}" for x, y in polygon)])
        for class_id, polygon in image_shapes
    )
    path.write_text("".join(f"{line}\n" for line in lines))


def read_labels(path: Path, size: tuple[int, int]) -> list[Shape]:
    width, height = size
    image_shapes = []
    for line in path.read_text().splitlines():
        class_id, *coords = line.split()
        xs, ys = coords[0::2], coords[1::2]
        polygon = [(float(x) * width, float(y) * height) for x, y in zip(xs, ys, strict=True)]
        image_shapes.append((int(class_id), polygon))
    return image_shapes
