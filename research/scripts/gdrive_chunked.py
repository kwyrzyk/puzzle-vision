"""Run dvc with pydrive2's Google Drive listing query split into chunks.

When dvc lists the remote, pydrive2 asks for the children of all prefix
directories (files/md5/00..ff) in one query `'id1' in parents or 'id2' in parents or ...`.
Google Drive rejects it with 400 Bad Request at ~150 ids, so the query is sent in chunks.

Usage:
    python scripts/gdrive_chunked.py push
    python scripts/gdrive_chunked.py status -c
"""

from collections.abc import Iterable
from itertools import chain, islice
import sys

from dvc.cli import main
from pydrive2.fs.spec import GDriveFileSystem

_CHUNK = 50
_list_ids = GDriveFileSystem._gdrive_list_ids


def _list_ids_chunked(self: GDriveFileSystem, query_ids: Iterable[str]):
    ids = iter(query_ids)
    chunks = iter(lambda: list(islice(ids, _CHUNK)), [])
    return chain.from_iterable(_list_ids(self, chunk) for chunk in chunks)


GDriveFileSystem._gdrive_list_ids = _list_ids_chunked

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
