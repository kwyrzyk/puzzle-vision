"""CVAT annotations of the raw dataset, read into CubeState.

Format: 'CVAT for images 1.1' XML, one task per cube, task name = cube_id.
Labels: 'face' is the cube outline, color names (white, red, ...) are stickers.
Photo convention: data/raw/README.md.
"""

from pathlib import Path
from typing import NamedTuple
import xml.etree.ElementTree as ET

from puzzle_vision.cube import COLOR, CUBE_SIZE, FACELETS_PER_FACE, CubeState, Face, face_view

type Point = tuple[float, float]  # image coordinates, y grows downwards

OUTLINE_LABEL = "face"
_FACE_OF_COLOR = {color: face for face, color in COLOR.items()}

# Face at the top of the photo
PHOTO_TOP: dict[Face, Face] = {
    Face.U: Face.B,
    Face.R: Face.U,
    Face.F: Face.U,
    Face.D: Face.R,
    Face.L: Face.U,
    Face.B: Face.U,
}


class Sticker(NamedTuple):
    color: Face
    polygon: list[Point]

    @property
    def center(self) -> Point:
        """Mean of the polygon vertices"""
        xs, ys = zip(*self.polygon, strict=True)
        return sum(xs) / len(xs), sum(ys) / len(ys)


class ImageAnnotation(NamedTuple):
    cube_id: str  # e.g. cube_0001
    face: Face  # photographed face
    stickers: list[Sticker]
    outlines: list[list[Point]]  # polygons labeled 'face', one expected


def _points(text: str) -> list[Point]:
    return [(float(x), float(y)) for x, y in (p.split(",") for p in text.split(";"))]


def _face_of_file(name: str) -> Face:
    """'1_white.jpg' -> U; CVAT project export may add a suffix: '1_white_7.jpg'"""
    return _FACE_OF_COLOR[Path(name).stem.split("_")[1]]


def parse_cvat(path: Path) -> list[ImageAnnotation]:
    root = ET.parse(path).getroot()
    cube_ids = {task.findtext("id"): task.findtext("name") or "" for task in root.iter("task")}
    images = []
    for image in root.iter("image"):
        polygons = [
            (p.attrib["label"], _points(p.attrib["points"])) for p in image.iter("polygon")
        ]
        stickers = [
            Sticker(_FACE_OF_COLOR[label], points)
            for label, points in polygons
            if label != OUTLINE_LABEL
        ]
        outlines = [points for label, points in polygons if label == OUTLINE_LABEL]
        cube_id = cube_ids[image.attrib["task_id"]]
        face = _face_of_file(image.attrib["name"])
        images.append(ImageAnnotation(cube_id, face, stickers, outlines))
    return images


def grid_order(points: list[Point]) -> list[int]:
    """Indices of 9 sticker centers in reading order of the photo.

    Sort by y into 3 rows, then by x within each row; works for moderate perspective
    skew and rotation.
    """
    if len(points) != FACELETS_PER_FACE:
        raise ValueError(f"expected {FACELETS_PER_FACE} stickers, got {len(points)}")
    by_y = sorted(range(FACELETS_PER_FACE), key=lambda i: points[i][1])
    rows = [by_y[r : r + CUBE_SIZE] for r in range(0, FACELETS_PER_FACE, CUBE_SIZE)]
    return [i for row in rows for i in sorted(row, key=lambda i: points[i][0])]


def set_face_colors(state: CubeState, image: ImageAnnotation) -> None:
    """Set colors of the photographed face; ValueError if there are not 9 stickers"""
    order = grid_order([sticker.center for sticker in image.stickers])
    view = face_view(image.face, PHOTO_TOP[image.face])
    for index, i in zip(view, order, strict=True):
        state.set_color(image.face, index, image.stickers[i].color)
