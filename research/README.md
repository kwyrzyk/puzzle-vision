# research

<a target="_blank" href="https://cookiecutter-data-science.drivendata.org/">
    <img src="https://img.shields.io/badge/CCDS-Project%20template-328F97?logo=cookiecutter" />
</a>

Data & research part of Engineering Thesis

## Getting started
Requirements: [uv](https://docs.astral.sh/uv/), access to the Google Drive data folder, DVC client secret (ask the author).

1. `uv sync`
2. Set the client secret: `uv run dvc remote modify --local gdrive gdrive_client_secret '<SECRET>'`
3. Pull data: `uv run dvc pull`. Details: [data/raw/README.md](data/raw/README.md#on-repo-clone).
4. `make test` – all tests pass, none skipped.

## Make commands
| command             | action                           |
|---------------------|----------------------------------|
| `make requirements` | install dependencies             |
| `make lint`         | ruff format check, ruff, pyright |
| `make format`       | ruff autofix + format            |
| `make test`         | pytest                           |

Full list: `make help`.

## Data
- `data/raw` – images and annotations in DVC (Google Drive), metadata in git. Details: [data/raw/README.md](data/raw/README.md).

## Project Organization

```
├── .dvc               <- DVC config, remote: Google Drive (secret in config.local)
├── Makefile           <- `make help` lists commands
├── pyproject.toml     <- package metadata, dependencies, ruff/pyright config
├── uv.lock
├── data
│   ├── raw            <- immutable input: images + annotations (DVC), metadata (git)
│   ├── interim        <- intermediate data
│   ├── processed      <- datasets for modeling
│   └── external       <- third party data
├── docs               <- documentation
├── models             <- trained models
├── notebooks          <- naming: `1.0-kw-short-description`
├── references         <- data dictionaries, manuals, papers
├── reports
│   └── figures        <- generated figures for the thesis
├── tests              <- pytest tests
└── puzzle_vision      <- source code
    └── config.py      <- project paths
```
