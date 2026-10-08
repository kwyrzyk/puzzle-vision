# Raw data
Raw cube images, annotations and metadata. Images and annotations are managed by `dvc` and kept on Google Drive. Only their signatures in files `annotations.dvc` and `images.dvc` are versioned in git.

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
When changing data or annotations, always run `uv run dvc push` before `git commit`. Example of an annotation change:
```bash
uv run dvc unprotect data/raw/annotations   # required because files are hardlinks to the DVC cache
# ... edit or replace files ...
uv run dvc add data/raw/annotations         # new hash in annotations.dvc (autostage runs git add)
uv run dvc push                             # upload data to Google Drive first
git commit
```

### Add new cube
To add a new cube directory:
```bash
uv run dvc unprotect data/raw/images
cp -r <new_cube_dir_path> data/raw/images/cube_0026
uv run dvc add data/raw/images
uv run dvc push
git commit
```

## Metadata
| file                   | key         | description                                              |
|------------------------|-------------|----------------------------------------------------------|
| `metadata/cubes.csv`   | `cube_id`   | one row per sample of 6 cube faces (`images/cube_NNNN/`) |
| `metadata/puzzles.csv` | `puzzle_id` | physical puzzle models used for the samples              |
| `metadata/cameras.csv` | `camera_id` | devices used to take the photos                          |

`cubes.csv` columns:

- `cube_id` – folder name in `images/`, `cube_` + 4-digit number,
- `puzzle_id` → `puzzles.csv`,
- `camera_id` → `cameras.csv`,
- `scramble` – sequence applied to the solved cube before taking photos.

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
