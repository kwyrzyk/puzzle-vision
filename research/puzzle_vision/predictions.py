"""YOLO segmentation predictions of Ultralytics, as annotations.

Results of model.predict are read by attributes, so Ultralytics is not a dependency.
Polygons are in pixels of the displayed photo: Ultralytics applies EXIF, like CVAT.
"""

from typing import Any, NamedTuple

import numpy as np
from PIL import Image, ImageDraw

from puzzle_vision.annotations import OUTLINE_LABEL, ImageAnnotation, Point, Sticker
from puzzle_vision.cube import COLOR
from puzzle_vision.export import CLASSES

FACE_OF_CLASS = {CLASSES.index(color): face for face, color in COLOR.items()}


class Detection(NamedTuple):
    class_id: int
    polygon: list[Point]  # pixels
    conf: float


def from_result(result: Any) -> list[Detection]:
    """Detections of an Ultralytics segmentation Results, of one photo"""
    if result.masks is None:  # nothing detected
        return []
    return [
        Detection(int(class_id), [(float(x), float(y)) for x, y in polygon], float(conf))
        for polygon, class_id, conf in zip(
            result.masks.xy, result.boxes.cls.tolist(), result.boxes.conf.tolist(), strict=True
        )
    ]


def polygon_iou(a: list[Point], b: list[Point]) -> float:
    """Intersection over union of the filled polygons, rasterized with 1 px"""
    a_xs, a_ys = zip(*a, strict=True)
    b_xs, b_ys = zip(*b, strict=True)
    # disjoint bounding boxes, no need to rasterize
    if (
        min(b_xs) > max(a_xs)
        or min(a_xs) > max(b_xs)
        or min(b_ys) > max(a_ys)
        or min(a_ys) > max(b_ys)
    ):
        return 0.0
    xs, ys = a_xs + b_xs, a_ys + b_ys
    left, top = int(min(xs)), int(min(ys))
    size = int(max(xs)) - left + 1, int(max(ys)) - top + 1
    masks = []
    for polygon in (a, b):
        mask = Image.new("1", size)
        ImageDraw.Draw(mask).polygon([(x - left, y - top) for x, y in polygon], fill=1)
        masks.append(np.asarray(mask))
    union = np.count_nonzero(masks[0] | masks[1])
    return np.count_nonzero(masks[0] & masks[1]) / union if union else 0.0


def deduplicate(detections: list[Detection], max_iou: float = 0.5) -> list[Detection]:
    """Of detections overlapping more than max_iou, of any class, keep the most confident"""
    kept: list[Detection] = []
    for detection in sorted(detections, key=lambda detection: -detection.conf):
        if all(polygon_iou(detection.polygon, other.polygon) <= max_iou for other in kept):
            kept.append(detection)
    return kept


def to_annotation(image: ImageAnnotation, detections: list[Detection]) -> ImageAnnotation:
    """The annotated photo with stickers and outlines from detections"""
    outline = CLASSES.index(OUTLINE_LABEL)
    return image._replace(
        stickers=[
            Sticker(FACE_OF_CLASS[d.class_id], d.polygon)
            for d in detections
            if d.class_id in FACE_OF_CLASS
        ],
        outlines=[d.polygon for d in detections if d.class_id == outline],
    )
