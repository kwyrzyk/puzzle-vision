from pathlib import Path

import pytest

from puzzle_vision.annotations import OUTLINE_LABEL, parse_cvat
from puzzle_vision.cube import COLOR
from puzzle_vision.export import CLASSES, WRITERS, export, register, shapes
from puzzle_vision.export.yolo import YoloSegWriter, read_labels

FIXTURE = Path(__file__).parent / "fixtures" / "cvat_annotations.xml"


def test_classes() -> None:
    assert sorted(CLASSES) == sorted([OUTLINE_LABEL, *COLOR.values()])


def test_shapes() -> None:
    photo = parse_cvat(FIXTURE)[0]
    found = shapes(photo)
    assert found[0] == (CLASSES.index("face"), photo.outlines[0])
    assert found[1] == (CLASSES.index("green"), photo.stickers[0].polygon)
    assert len(found) == 10


def test_writers_registered() -> None:
    assert {"yolo-seg": YoloSegWriter} == WRITERS


def test_register_twice_fail() -> None:
    with pytest.raises(ValueError):
        register("yolo-seg")(YoloSegWriter)


@pytest.fixture
def images_dir(tmp_path: Path) -> Path:
    """Fake photos of the fixture cube"""
    cube_dir = tmp_path / "raw" / "cube_0001"
    cube_dir.mkdir(parents=True)
    for name in ("1_white.jpg", "2_green.jpg"):
        (cube_dir / name).write_bytes(name.encode())
    return tmp_path / "raw"


def test_yolo_export(tmp_path: Path, images_dir: Path) -> None:
    out = tmp_path / "yolo"
    photo = parse_cvat(FIXTURE)[0]
    export([photo], {"cube_0001": "val"}, YoloSegWriter(out), images_dir)

    assert (out / "images" / "val" / "cube_0001_1_white.jpg").read_bytes() == b"1_white.jpg"
    labels = (out / "labels" / "val" / "cube_0001_1_white.txt").read_text().splitlines()
    assert len(labels) == 10
    assert labels[0] == "0 0.000000 0.000000 1.000000 0.000000 1.000000 1.000000 0.000000 1.000000"
    assert (out / "data.yaml").read_text() == (
        "val: images/val\nnames:\n"
        "  0: face\n  1: white\n  2: green\n  3: orange\n  4: blue\n  5: red\n  6: yellow\n"
    )


def test_yolo_labels_roundtrip(tmp_path: Path, images_dir: Path) -> None:
    photo = parse_cvat(FIXTURE)[0]
    export([photo], {"cube_0001": "train"}, YoloSegWriter(tmp_path / "yolo"), images_dir)
    found = read_labels(
        tmp_path / "yolo" / "labels" / "train" / "cube_0001_1_white.txt", photo.size
    )
    expected = shapes(photo)
    assert [class_id for class_id, _ in found] == [class_id for class_id, _ in expected]
    for (_, polygon), (_, expected_polygon) in zip(found, expected, strict=True):
        assert polygon == [pytest.approx(point, abs=0.01) for point in expected_polygon]
