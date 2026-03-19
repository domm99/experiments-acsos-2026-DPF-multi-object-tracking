#!/usr/bin/env python3
"""Plot paired GPS and planar zebra trajectories into PDF charts."""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib.collections import LineCollection


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create one PDF per zebra trajectory with two subplots: GPS lat/lon "
            "and planar x/y, both colored with a viridis gradient over time."
        )
    )
    parser.add_argument(
        "--gps-root",
        type=Path,
        default=Path("output"),
        help="Root directory with GPS CSV files. Default: output",
    )
    parser.add_argument(
        "--xy-root",
        type=Path,
        default=Path("output_xy"),
        help="Root directory with planar CSV files. Default: output_xy",
    )
    parser.add_argument(
        "--chart-root",
        type=Path,
        default=Path("chart"),
        help="Root directory where PDF charts will be saved. Default: chart",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=200,
        help="Figure DPI. Default: 200",
    )
    return parser.parse_args()


def iter_gps_csv_files(gps_root: Path) -> list[Path]:
    return sorted(path for path in gps_root.rglob("*.csv") if path.is_file())


def build_segments(points: np.ndarray) -> np.ndarray | None:
    if len(points) < 2:
        return None
    return np.stack([points[:-1], points[1:]], axis=1)


def expand_axis_limits(values: np.ndarray) -> tuple[float, float]:
    minimum = float(values.min())
    maximum = float(values.max())
    if minimum == maximum:
        pad = max(abs(minimum) * 0.01, 1e-6)
        return minimum - pad, maximum + pad
    pad = (maximum - minimum) * 0.05
    return minimum - pad, maximum + pad


def add_colored_trajectory(
    ax: plt.Axes,
    x_values: np.ndarray,
    y_values: np.ndarray,
    xlabel: str,
    ylabel: str,
    title: str,
) -> None:
    ax.set_title(title)
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, alpha=0.25)
    ax.set_aspect("equal", adjustable="box")

    if len(x_values) == 0:
        return

    points = np.column_stack([x_values, y_values])
    segments = build_segments(points)

    if segments is not None:
        color_steps = np.arange(len(segments))
        collection = LineCollection(
            segments,
            cmap="viridis",
            norm=plt.Normalize(color_steps.min(), color_steps.max() or 1),
            linewidths=2.0,
        )
        collection.set_array(color_steps)
        ax.add_collection(collection)
    else:
        ax.plot(x_values, y_values, color=plt.cm.viridis(0.0), linewidth=2.0)

    scatter_steps = np.linspace(0.0, 1.0, num=len(points))
    ax.scatter(x_values, y_values, c=scatter_steps, cmap="viridis", s=8, zorder=3)
    ax.scatter(x_values[0], y_values[0], color=plt.cm.viridis(0.0), s=30, zorder=4)
    ax.scatter(x_values[-1], y_values[-1], color=plt.cm.viridis(1.0), s=30, zorder=4)
    ax.set_xlim(*expand_axis_limits(x_values))
    ax.set_ylim(*expand_axis_limits(y_values))


def validate_columns(frame: pd.DataFrame, required: set[str], path: Path) -> None:
    missing = required - set(frame.columns)
    if missing:
        missing_list = ", ".join(sorted(missing))
        raise ValueError(f"{path}: missing required columns: {missing_list}")


def plot_pair(gps_csv: Path, xy_csv: Path, destination: Path, dpi: int) -> int:
    gps_frame = pd.read_csv(gps_csv)
    xy_frame = pd.read_csv(xy_csv)

    validate_columns(gps_frame, {"lat", "lon", "timestamp"}, gps_csv)
    validate_columns(xy_frame, {"x", "y", "timestamp"}, xy_csv)

    point_count = min(len(gps_frame), len(xy_frame))
    if point_count == 0:
        return 0

    gps_frame = gps_frame.iloc[:point_count].copy()
    xy_frame = xy_frame.iloc[:point_count].copy()

    fig, axes = plt.subplots(1, 2, figsize=(13, 6), constrained_layout=True)
    fig.suptitle(f"{gps_csv.parent.name} | {gps_csv.stem} | {point_count} points")

    add_colored_trajectory(
        axes[0],
        gps_frame["lon"].to_numpy(dtype=float),
        gps_frame["lat"].to_numpy(dtype=float),
        xlabel="Longitude",
        ylabel="Latitude",
        title="GPS trajectory",
    )
    add_colored_trajectory(
        axes[1],
        xy_frame["x"].to_numpy(dtype=float),
        xy_frame["y"].to_numpy(dtype=float),
        xlabel="x (m)",
        ylabel="y (m)",
        title="Planar trajectory",
    )

    destination.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(destination, format="pdf", dpi=dpi, bbox_inches="tight")
    plt.close(fig)
    return point_count


def main() -> int:
    args = parse_args()
    gps_root = args.gps_root.resolve()
    xy_root = args.xy_root.resolve()
    chart_root = args.chart_root.resolve()

    gps_csv_files = iter_gps_csv_files(gps_root)
    if not gps_csv_files:
        raise SystemExit(f"No GPS CSV files found under {gps_root}")

    created = 0
    skipped = 0

    for gps_csv in gps_csv_files:
        relative_path = gps_csv.relative_to(gps_root)
        xy_csv = xy_root / relative_path
        if not xy_csv.exists():
            skipped += 1
            print(f"Skipping {relative_path}: matching XY CSV not found")
            continue

        pdf_path = (chart_root / relative_path).with_suffix(".pdf")
        point_count = plot_pair(gps_csv, xy_csv, pdf_path, dpi=args.dpi)
        created += 1
        print(f"{relative_path} -> {pdf_path.relative_to(chart_root)} ({point_count} points)")

    print(f"Completed: {created} PDF charts created, {skipped} files skipped.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
