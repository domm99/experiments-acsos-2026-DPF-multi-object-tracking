#!/usr/bin/env python3
"""Count, for each flight, how many zebra trajectories exceed a step threshold."""

from __future__ import annotations

import csv
import shutil
from pathlib import Path

# Set this value directly in the code before running the script.
MIN_STEPS = 100

# Change this if you want to analyze a different CSV tree, for example output_xy.
INPUT_ROOT = Path("output_xy")

# If True, matching CSV files are copied to OUTPUT_ROOT while preserving folder structure.
EXPORT_MATCHES = False
OUTPUT_ROOT = Path("output_filtered")


def count_csv_rows(csv_path: Path) -> int:
    with csv_path.open("r", newline="", encoding="utf-8") as handle:
        reader = csv.reader(handle)
        next(reader, None)
        return sum(1 for _ in reader)


def main() -> int:
    input_root = INPUT_ROOT.resolve()
    if not input_root.exists():
        raise SystemExit(f"Input directory not found: {input_root}")

    output_root = OUTPUT_ROOT.resolve()

    flight_dirs = sorted(
        path for path in input_root.rglob("*") if path.is_dir() and "zebras" in path.name.lower()
    )
    if not flight_dirs:
        raise SystemExit(f"No zebra flight directories found under {input_root}")

    for flight_dir in flight_dirs:
        csv_files = sorted(flight_dir.glob("*.csv"))
        total_zebras = len(csv_files)
        zebras_above_threshold: list[str] = []

        for csv_file in csv_files:
            step_count = count_csv_rows(csv_file)
            if step_count > MIN_STEPS:
                zebras_above_threshold.append(csv_file.stem)
                if EXPORT_MATCHES:
                    destination = output_root / csv_file.relative_to(input_root)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(csv_file, destination)

        relative_flight = flight_dir.relative_to(input_root)
        print(
            f"{relative_flight}: {len(zebras_above_threshold)} zebre con piu di {MIN_STEPS} step "
            f"su {total_zebras} traiettorie"
        )
        #if zebras_above_threshold:
        #    print("  " + ", ".join(zebras_above_threshold))
        #else:
        #    print("  Nessuna")

    if EXPORT_MATCHES:
        print(f"\nCSV esportati in: {output_root}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
