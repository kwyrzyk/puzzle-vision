from pathlib import Path

from dotenv import load_dotenv
from loguru import logger

# Load environment variables from .env file if it exists
load_dotenv()

# Paths
PROJ_ROOT = Path(__file__).resolve().parents[1]
logger.info(f"PROJ_ROOT path is: {PROJ_ROOT}")

DATA_DIR = PROJ_ROOT / "data"
RAW_DATA_DIR = DATA_DIR / "raw"
INTERIM_DATA_DIR = DATA_DIR / "interim"
PROCESSED_DATA_DIR = DATA_DIR / "processed"
EXTERNAL_DATA_DIR = DATA_DIR / "external"

RAW_IMAGES_DIR = RAW_DATA_DIR / "images"
RAW_ANNOTATIONS_DIR = RAW_DATA_DIR / "annotations"
RAW_VIDEOS_DIR = RAW_DATA_DIR / "videos"
RAW_METADATA_DIR = RAW_DATA_DIR / "metadata"

PUZZLE_3X3, PUZZLE_CLOCK = "3x3", "clock"
PUZZLE_TYPES = (PUZZLE_3X3, PUZZLE_CLOCK)


def raw_images_dir(puzzle: str) -> Path:
    return RAW_IMAGES_DIR / puzzle


def raw_videos_dir(puzzle: str) -> Path:
    return RAW_VIDEOS_DIR / puzzle


def cvat_annotations(puzzle: str) -> Path:
    return RAW_ANNOTATIONS_DIR / f"{puzzle}.xml"


def raw_metadata(puzzle: str, table: str = "") -> Path:
    """metadata/<type>.csv (the samples), or metadata/<type>_<table>.csv"""
    return RAW_METADATA_DIR / f"{puzzle}{f'_{table}' if table else ''}.csv"


MODELS_DIR = PROJ_ROOT / "models"

REPORTS_DIR = PROJ_ROOT / "reports"
FIGURES_DIR = REPORTS_DIR / "figures"

# If tqdm is installed, configure loguru with tqdm.write
# https://github.com/Delgan/loguru/issues/135
try:
    from tqdm import tqdm

    logger.remove(0)
    logger.add(lambda msg: tqdm.write(msg, end=""), colorize=True)
except ModuleNotFoundError:
    pass
