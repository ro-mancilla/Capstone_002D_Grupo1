"""Kaggle download for the Favorita competition data.

Deliberately thin: it shells out to the official Kaggle CLI rather than reimplementing the auth and
download protocol. The competition data is ~5 GB and requires an account that has *accepted the
competition rules* -- a step that cannot be automated, and should not be, since accepting a licence
on someone's behalf is not a thing a script should do.

Licensing note (plan 08 §2): Kaggle competition data is governed by that competition's rules, which
generally permit academic and non-commercial use but must be read. The terms as they applied to this
project are recorded in README.md. Raw data is never committed -- `data/` is gitignored and only
derived metrics and plots enter the repository, which is safe under any of the plausible terms.
"""

import subprocess
import zipfile
from pathlib import Path

COMPETITION = "favorita-grocery-sales-forecasting"

# Only the tables the pipeline reads. `transactions.csv` is unpacked too because plan 08 §2 lists it
# as an optional feature source, but nothing currently consumes it.
WANTED = ("train.csv", "items.csv", "stores.csv", "holidays_events.csv", "transactions.csv")


def download(raw_dir: Path, *, competition: str = COMPETITION) -> Path:
    """Fetch and unpack the competition archive into `raw_dir`.

    Fails loudly with an actionable message when the CLI is missing or unauthenticated, because the
    two realistic failures here are both fixable by the user in one step and neither is worth a
    stack trace.
    """
    raw_dir.mkdir(parents=True, exist_ok=True)
    archive = raw_dir / f"{competition}.zip"

    if not archive.exists():
        try:
            subprocess.run(
                ["kaggle", "competitions", "download", "-c", competition, "-p", str(raw_dir)],
                check=True,
            )
        except FileNotFoundError as error:
            raise SystemExit(
                "the `kaggle` CLI is not installed.\n"
                "  uv sync --extra data\n"
                "then place your API token at ~/.kaggle/kaggle.json (Kaggle > Account > Create API "
                "Token) and accept the competition rules on the competition page."
            ) from error
        except subprocess.CalledProcessError as error:
            raise SystemExit(
                f"kaggle download failed (exit {error.returncode}).\n"
                "Most likely: the competition rules have not been accepted on your Kaggle account. "
                "Open the competition page, accept, and retry."
            ) from error

    _unpack(archive, raw_dir)
    return raw_dir


def _unpack(archive: Path, raw_dir: Path) -> None:
    """Unpack the competition archive down to plain CSVs.

    Two levels, because that is how Kaggle actually ships this competition and it is not what the
    obvious code assumes. The download is a single `.zip`, but its members are not the CSVs -- they
    are **`.7z` archives**, one per table (`train.csv.7z`, `items.csv.7z`, ...). A `name.endswith(
    "train.csv")` filter therefore matches nothing at all and extracts an empty directory, which
    only surfaces later as "missing raw files" from the preprocessor, pointing at the wrong step.

    Handled defensively rather than assumed: members that are already plain CSVs are taken as-is,
    so this keeps working if Kaggle ever re-packages the competition (they have re-packaged it once
    already -- the current files are dated 2019, two years after the competition closed).

    py7zr rather than a `7z` subprocess: macOS ships no 7z binary, and requiring `brew install
    p7zip` to reproduce a thesis result is a worse dependency than a wheel.
    """
    import py7zr

    with zipfile.ZipFile(archive) as bundle:
        members = bundle.namelist()

        for wanted in WANTED:
            target = raw_dir / wanted
            if target.exists():
                print(f"  {wanted} already unpacked")
                continue

            plain = next((m for m in members if Path(m).name == wanted), None)
            if plain is not None:
                with bundle.open(plain) as source, target.open("wb") as sink:
                    sink.write(source.read())
                print(f"  {wanted} <- {plain}")
                continue

            compressed = next((m for m in members if Path(m).name == f"{wanted}.7z"), None)
            if compressed is None:
                # Not fatal: `transactions.csv` is optional, and a table the pipeline needs will be
                # reported by the preprocessor with a message naming the file.
                print(f"  {wanted} not in the archive — skipping")
                continue

            # Extract the .7z to a scratch path first; py7zr needs a real file, not a zip stream.
            nested = raw_dir / Path(compressed).name
            with bundle.open(compressed) as source, nested.open("wb") as sink:
                sink.write(source.read())
            # train.csv is 474 MB compressed and ~4.7 GB out; the rest are seconds.
            note = " — 474 MB, expect a few minutes" if wanted == "train.csv" else ""
            print(f"  {wanted} <- {compressed} (decompressing{note})")
            with py7zr.SevenZipFile(nested, mode="r") as seven:
                seven.extractall(path=raw_dir)
            nested.unlink()


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", type=Path, default=Path("data/raw"))
    args = parser.parse_args()
    path = download(args.raw_dir)
    print(f"raw data in {path}")


if __name__ == "__main__":
    main()
