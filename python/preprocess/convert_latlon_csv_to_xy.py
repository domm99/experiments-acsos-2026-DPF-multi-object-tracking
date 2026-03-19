#!/usr/bin/env python3
"""Convert latitude/longitude zebra trajectories to a local 2D plane."""

from __future__ import annotations

import argparse
import csv
import math
import sys
from pathlib import Path

EARTH_RADIUS_METERS = 6_371_000.0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Read zebra trajectory CSV files with lat/lon/timestamp and write "
            "matching CSV files with x/y/timestamp in meters."
        )
    )
    parser.add_argument(
        "--input-root",
        type=Path,
        default=Path("output"),
        help="Directory containing geographic CSV files. Default: output",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path("output_xy"),
        help="Directory where planar CSV files will be written. Default: output_xy",
    )
    return parser.parse_args()


def find_csv_files(input_root: Path) -> list[Path]:
    return sorted(path for path in input_root.rglob("*.csv") if path.is_file())


def latlon_to_local_xy(
    lat_deg: float, lon_deg: float, origin_lat_deg: float, origin_lon_deg: float
) -> tuple[float, float]:
    lat = math.radians(lat_deg)
    lon = math.radians(lon_deg)
    origin_lat = math.radians(origin_lat_deg)
    origin_lon = math.radians(origin_lon_deg)

    delta_lat = lat - origin_lat
    delta_lon = lon - origin_lon

    x = EARTH_RADIUS_METERS * delta_lon * math.cos(origin_lat)
    y = EARTH_RADIUS_METERS * delta_lat
    return x, y


def convert_csv(csv_path: Path, input_root: Path, output_root: Path) -> int:
    with csv_path.open("r", newline="", encoding="utf-8") as source_handle:
        reader = csv.DictReader(source_handle)
        rows = list(reader)

    if not rows:
        return 0

    required_fields = {"lat", "lon", "timestamp"}
    if not required_fields.issubset(reader.fieldnames or set()):
        missing = sorted(required_fields - set(reader.fieldnames or []))
        raise ValueError(f"{csv_path}: missing required columns: {', '.join(missing)}")

    origin_lat = float(rows[0]["lat"])
    origin_lon = float(rows[0]["lon"])

    relative_path = csv_path.relative_to(input_root)
    destination = output_root / relative_path
    destination.parent.mkdir(parents=True, exist_ok=True)

    with destination.open("w", newline="", encoding="utf-8") as destination_handle:
        writer = csv.writer(destination_handle)
        writer.writerow(["x", "y", "timestamp"])
        for row in rows:
            x, y = latlon_to_local_xy(
                float(row["lat"]),
                float(row["lon"]),
                origin_lat,
                origin_lon,
            )
            writer.writerow([f"{x:.6f}", f"{y:.6f}", row["timestamp"]])

    return len(rows)


def main() -> int:
    args = parse_args()
    input_root = args.input_root.resolve()
    output_root = args.output_root.resolve()

    if not input_root.exists():
        print(f"Input directory not found: {input_root}", file=sys.stderr)
        return 1

    csv_files = find_csv_files(input_root)
    if not csv_files:
        print(f"No CSV files found under {input_root}", file=sys.stderr)
        return 1

    total_points = 0
    for csv_file in csv_files:
        point_count = convert_csv(csv_file, input_root, output_root)
        total_points += point_count
        print(f"{csv_file.relative_to(input_root)} -> {point_count} points converted")

    print(
        f"Completed: {len(csv_files)} CSV files converted, {total_points} points written."
    )
    print(
        "Projection used: local equirectangular approximation centered on the first "
        "point of each trajectory."
    )
    print("x = R * cos(lat0) * (lon - lon0), y = R * (lat - lat0), with angles in radians.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
