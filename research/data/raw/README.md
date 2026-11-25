# Raw data
Raw puzzle images, videos, annotations and metadata. Images, videos and annotations are managed by `dvc` and kept on Google Drive. Only their signatures in files `images.dvc`, `videos.dvc` and `annotations.dvc` are versioned in git.

## Layout
The data is split by puzzle type – the `type` column of `metadata/puzzles.csv` (`3x3`, `clock`).
A sample is one scrambled puzzle photographed from all sides, `cube_NNNN` numbered from 1 within
its type (also for the clock), so a sample is identified by `<type>/cube_NNNN`:

```
images/<type>/cube_NNNN/        photos of the sample
videos/<type>/cube_NNNN/        videos of the sample, <video_id>.mp4
annotations/<type>.xml          CVAT export, one CVAT project per type, task name = cube_NNNN
metadata/<type>.csv             one row per sample
metadata/<type>_videos.csv      one row per video
```

## DVC commands

### On repo clone
To download the data after cloning the repository, run:
```bash
uv sync
uv run dvc remote modify --local gdrive gdrive_client_secret '<SECRET>'
uv run dvc pull
```
A Google Drive client secret with appropriate access is required.

### Data change
When changing data or annotations, always run `make push` (`dvc push` with a workaround for the Google Drive 400 Bad Request, see `scripts/gdrive_chunked.py`) before `git commit`. Example of an annotation change:
```bash
uv run dvc unprotect data/raw/annotations   # required because files are hardlinks to the DVC cache
# ... edit or replace files ...
uv run dvc add data/raw/annotations         # new hash in annotations.dvc (autostage runs git add)
make push                                   # upload data to Google Drive first
make data                                   # processed data e.g. yolo export depends on data/raw/annotations
git commit
```

### Add new samples
To add a new sample directory:
```bash
uv run dvc unprotect data/raw/images        # or data/raw/videos
cp -r <new_sample_dir_path> data/raw/images/3x3/cube_0036
uv run dvc add data/raw/images
make push
make data                                   # processed data e.g. yolo export depends on data/raw/images
git commit
```
A flat directory of photos taken in shooting order (6 per cube, 2 per clock) can be split into
`images/<type>/cube_NNNN/` directories with `scripts/sort_cubes.py --puzzle <type>` (numbering
continues after the last sample of the type, `--dry-run` to preview). `scripts/create_tasks.py
--puzzle <type>` then creates one CVAT task per new sample (skips existing tasks, password asked
interactively).

## Metadata
| file                        | key                 | description                                       |
|-----------------------------|---------------------|---------------------------------------------------|
| `metadata/puzzles.csv`      | `puzzle_id`         | physical puzzles used for the samples, with `type` |
| `metadata/cameras.csv`      | `camera_id`         | devices used to take the photos                   |
| `metadata/3x3.csv`          | `cube_id`           | one row per 3x3 sample (`images/3x3/cube_NNNN/`)  |
| `metadata/3x3_videos.csv`   | `cube_id, video_id` | one row per video of a 3x3 sample                 |
| `metadata/clock.csv`        | `cube_id`           | one row per Rubik's Clock sample (`images/clock/cube_NNNN/`) |

`<type>.csv` columns:

- `cube_id` – folder name in `images/<type>/`, `cube_` + 4-digit number,
- `puzzle_id` → `puzzles.csv`, a puzzle of this type,
- `camera_id` → `cameras.csv`,
- `scramble` – sequence applied to the solved puzzle before taking photos.

### Convention
Scrambles are in official WCA (World Cube Association) convention, each of the letters U (up), F (front), R (right), B (back), L (left), D (down) describes a clockwise move of the given face, a letter with ' indicates a counter-clockwise move, e.g. R'. A letter followed by 2 indicates a double move (180°), e.g. F2.

Before scrambling, the cube needs to be held with the white face up and the green face in front. The "color" of a face is the color of its center, as it is the only non-moving element of a given face.

| file           | center | cube face |
|----------------|--------|-----------|
| `1_white.jpg`  | white  | U         |
| `2_green.jpg`  | green  | F         |
| `3_orange.jpg` | orange | L         |
| `4_blue.jpg`   | blue   | B         |
| `5_red.jpg`    | red    | R         |
| `6_yellow.jpg` | yellow | D         |

### Photo orientation
Face at the top of the frame:

| file                                     | top of frame |
|------------------------------------------|--------------|
| `1_white.jpg`                            | blue (B)     |
| `2_green`, `3_orange`, `4_blue`, `5_red` | white (U)    |
| `6_yellow.jpg`                           | red (R)      |

Annotation coordinates are in display space – mind the EXIF Orientation when loading, e.g. ImageOps.exif_transpose (Pillow).

## Rubik's Clock
One sample = one scrambled clock, 2 photos in `images/clock/cube_NNNN/`, a row in
`metadata/clock.csv`.

| file          | side                                                         |
|---------------|--------------------------------------------------------------|
| `1_front.jpg` | facing the scrambler at the start of the scramble            |
| `2_back.jpg`  | the other side, after turning the clock around the vertical axis (`y2`) |

12 o'clock at the top of the frame on both photos. Before scrambling all dials show 12.

Scrambles in WCA notation: `UR3+` – pin UR up (others down), the dials turned clockwise (`+`) or
counterclockwise (`-`) by 3 hours; `U`, `R`, `D`, `L` – two pins of that side up, `ALL` – all;
`y2` – the clock turned around (the back faces the scrambler). Pin tokens without an amount at
the end of older scrambles give the final pin positions.

Annotations: separate CVAT project `rubiks-clock-dataset`, export `annotations/clock.xml`,
one task per clock (task name = `cube_NNNN`). On every photo:

| label          | shape     | what                                                                 |
|----------------|-----------|----------------------------------------------------------------------|
| `clock`        | polygon   | outline of the body of the clock (with the wheels at the rim)        |
| `1` ... `12`   | rectangle | each of the 9 dials: axis-aligned, tight around the dial with the hour marks around it; the label is the hour the hand shows (`12` – no turn) |

Hours are read as on the photo, 12 o'clock at the top of the frame; they must agree with the
scramble.
