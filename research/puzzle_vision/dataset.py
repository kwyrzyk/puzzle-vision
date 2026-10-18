"""Training dataset from the raw data; see `python -m puzzle_vision.dataset --help`."""

from pathlib import Path
from typing import Annotated

from loguru import logger
import typer

from puzzle_vision.annotations import ImageAnnotation, parse_cvat
from puzzle_vision.config import CVAT_ANNOTATIONS, PROCESSED_DATA_DIR, RAW_IMAGES_DIR
from puzzle_vision.export import WRITERS, export
from puzzle_vision.split import split_cubes

app = typer.Typer()


def annotated_images(path: Path) -> list[ImageAnnotation]:
    """Photos of cubes with stickers on every photo, the other cubes are not annotated yet"""
    images = parse_cvat(path)
    not_annotated = {image.cube_id for image in images if not image.stickers}
    if not_annotated:
        logger.warning(f"Skipped cubes, not annotated: {', '.join(sorted(not_annotated))}")
    return [image for image in images if image.cube_id not in not_annotated]


@app.command()
def main(
    fmt: Annotated[str, typer.Option("--format", help=f"One of: {', '.join(WRITERS)}")] = (
        "yolo-seg"
    ),
    output: Annotated[Path | None, typer.Option(help="Default: data/processed/<format>")] = None,
    train: float = 0.6,
    val: float = 0.2,
    seed: Annotated[int | None, typer.Option(help="Default: random split")] = None,
) -> None:
    """Export annotated photos to a training dataset, split by cube; test gets the rest."""
    if fmt not in WRITERS:
        raise typer.BadParameter(f"unknown format {fmt!r}, known: {', '.join(WRITERS)}")
    output = output or PROCESSED_DATA_DIR / fmt.replace("-", "_")
    if output.exists():
        raise typer.BadParameter(f"{output} exists, remove it first")
    images = annotated_images(CVAT_ANNOTATIONS)
    cube_ids = sorted({image.cube_id for image in images})
    split_of = split_cubes(cube_ids, train, val, seed=seed)
    export(images, split_of, WRITERS[fmt](output), RAW_IMAGES_DIR)
    logger.success(f"Exported {len(images)} photos of {len(cube_ids)} cubes to {output}")


if __name__ == "__main__":
    app()
