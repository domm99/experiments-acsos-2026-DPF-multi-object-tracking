#!/usr/bin/env python3
"""Extract zebra trajectories from GPX files into per-track CSV files."""

from __future__ import annotations

import argparse
import csv
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

GPX_NS = {"gpx": "http://www.topografix.com/GPX/1/1"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Find GPX files inside zebra flight folders and export each GPX track "
            "as a separate CSV with lat, lon, timestamp columns."
        )
    )
    parser.add_argument(
        "--input-root",
        type=Path,
        default=Path("trajectories"),
        help="Root directory containing flight folders. Default: trajectories",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path("output"),
        help="Root directory where CSV files will be written. Default: output",
    )
    return parser.parse_args()


def find_zebra_gpx_files(input_root: Path) -> list[Path]:
    gpx_files: list[Path] = []
    for path in input_root.rglob("*.gpx"):
        parent_name = path.parent.name.lower()
        stem = path.stem.lower()
        if "zebras" in parent_name and "zebra" in stem:
            gpx_files.append(path)
    return sorted(gpx_files)


def extract_track_points(track: ET.Element) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    for segment in track.findall("gpx:trkseg", GPX_NS):
        for point in segment.findall("gpx:trkpt", GPX_NS):
            timestamp = point.findtext("gpx:time", default="", namespaces=GPX_NS)
            lat = point.attrib.get("lat")
            lon = point.attrib.get("lon")
            if lat is None or lon is None or not timestamp:
                continue
            rows.append((lat, lon, timestamp))
    return rows


def write_track_csv(destination: Path, rows: list[tuple[str, str, str]]) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["lat", "lon", "timestamp"])
        writer.writerows(rows)


def convert_file(gpx_path: Path, input_root: Path, output_root: Path) -> tuple[int, int]:
    relative_parent = gpx_path.parent.relative_to(input_root)
    destination_dir = output_root / relative_parent

    tree = ET.parse(gpx_path)
    root = tree.getroot()
    tracks = root.findall("gpx:trk", GPX_NS)

    written = 0
    skipped = 0
    for index, track in enumerate(tracks, start=1):
        rows = extract_track_points(track)
        if not rows:
            skipped += 1
            continue

        csv_path = destination_dir / f"zebra_{index:03d}.csv"
        write_track_csv(csv_path, rows)
        written += 1

    return written, skipped


def main() -> int:
    args = parse_args()
    input_root = args.input_root.resolve()
    output_root = args.output_root.resolve()

    if not input_root.exists():
        print(f"Input directory not found: {input_root}", file=sys.stderr)
        return 1

    gpx_files = find_zebra_gpx_files(input_root)
    if not gpx_files:
        print(f"No zebra GPX files found under {input_root}", file=sys.stderr)
        return 1

    total_written = 0
    total_skipped = 0

    for gpx_file in gpx_files:
        written, skipped = convert_file(gpx_file, input_root, output_root)
        total_written += written
        total_skipped += skipped
        print(
            f"{gpx_file.relative_to(input_root)} -> {written} CSV files"
            + (f" ({skipped} empty tracks skipped)" if skipped else "")
        )

    print(
        f"Completed: {len(gpx_files)} GPX files processed, "
        f"{total_written} CSV files written, {total_skipped} tracks skipped."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
