from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

from puzzle_vision.annotations import Point, parse_cvat
from puzzle_vision.cube import Face
from puzzle_vision.predictions import (
    Detection,
    deduplicate,
    from_result,
    polygon_iou,
    to_annotation,
)

FIXTURE = Path(__file__).parent / "fixtures" / "cvat_annotations.xml"


def square(x: float, y: float, side: float = 100) -> list[Point]:
    return [(x, y), (x + side, y), (x + side, y + side), (x, y + side)]


def test_from_result() -> None:
    # attributes of ultralytics.engine.results.Results used by from_result
    polygons = [np.array([[20.0, 20.0], [60.0, 20.0], [60.0, 40.0]]), np.zeros((3, 2))]
    boxes = SimpleNamespace(cls=np.array([1.0, 0.0]), conf=np.array([0.9, 0.4]))
    result = SimpleNamespace(masks=SimpleNamespace(xy=polygons), boxes=boxes)
    first, second = from_result(result)
    assert first == Detection(1, [(20, 20), (60, 20), (60, 40)], 0.9)
    assert second == Detection(0, [(0, 0), (0, 0), (0, 0)], 0.4)


def test_from_result_nothing_detected() -> None:
    assert from_result(SimpleNamespace(masks=None, boxes=None)) == []


def test_polygon_iou() -> None:
    assert polygon_iou(square(0, 0), square(0, 0)) == 1
    assert polygon_iou(square(0, 0), square(200, 0)) == 0
    assert polygon_iou(square(0, 0), square(50, 0)) == pytest.approx(1 / 3, abs=0.01)


def test_deduplicate() -> None:
    white = Detection(1, square(0, 0), 0.9)
    yellow = Detection(6, square(10, 0), 0.6)  # the same sticker, other color
    green = Detection(2, square(200, 0), 0.5)
    assert deduplicate([yellow, green, white]) == [white, green]


def test_to_annotation() -> None:
    photo = parse_cvat(FIXTURE)[0]
    outline = Detection(0, square(0, 0, 300), 0.8)
    predicted = to_annotation(photo, [outline, Detection(2, square(0, 0), 0.9)])
    assert predicted._replace(stickers=[], outlines=[]) == photo._replace(stickers=[], outlines=[])
    assert predicted.outlines == [outline.polygon]
    assert [(sticker.color, sticker.polygon) for sticker in predicted.stickers] == [
        (Face.F, square(0, 0))
    ]
