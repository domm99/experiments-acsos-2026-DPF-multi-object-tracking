from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns


DEFAULT_ZEBRAS = (35, 37, 38)
DEFAULT_NEIGHBORS = (0, 1, 4, 7)
ESTIMATION_PATTERN = re.compile(
    r"estimations_zebra(?P<zebra>\d+)_node-(?P<node>\d+)_n-(?P<neighbors>\d+)"
    r"_errorOnPosition-(?P<error>[\d.]+)_seed-(?P<seed>[\d.]+)\.csv$"
)


@dataclass(frozen=True)
class EstimationFile:
    path: Path
    zebra_id: int
    node_id: int
    neighbors: int
    seed: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot fixedSensorsNB RMSE over time for different numberOfNeighbors values."
    )
    parser.add_argument(
        "--trajectory-dir",
        type=Path,
        default=Path("src/main/resources/zebras-trajectories/flights/flight_1_zebras"),
        help="Directory containing real zebra trajectory CSV files.",
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("data/fixedSensorsNB"),
        help="Directory containing fixedSensorsNB estimation CSV files.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("charts/fixedSensorsNB"),
        help="Directory where the plot is written.",
    )
    parser.add_argument(
        "--filename",
        default="rmse_over_time_by_number_of_neighbors",
        help="Output filename stem.",
    )
    parser.add_argument(
        "--zebras",
        nargs="+",
        type=int,
        default=DEFAULT_ZEBRAS,
        help="Zebra IDs to include in the RMSE.",
    )
    parser.add_argument(
        "--neighbors",
        nargs="+",
        type=int,
        default=DEFAULT_NEIGHBORS,
        help="Neighbor-count values to plot.",
    )
    parser.add_argument(
        "--error-on-position",
        default="0.0",
        help="errorOnPosition value in filenames.",
    )
    parser.add_argument(
        "--max-time-step",
        type=int,
        default=1400,
        help="Maximum number of time steps to include.",
    )
    parser.add_argument(
        "--rolling-window",
        type=int,
        default=21,
        help="Centered rolling window used only to smooth plotted curves.",
    )
    return parser.parse_args()


def parse_estimation_file(path: Path) -> EstimationFile | None:
    match = ESTIMATION_PATTERN.match(path.name)
    if not match:
        return None
    metadata = match.groupdict()
    return EstimationFile(
        path=path,
        zebra_id=int(metadata["zebra"]),
        node_id=int(metadata["node"]),
        neighbors=int(metadata["neighbors"]),
        seed=metadata["seed"],
    )


def read_xy_csv(path: Path, columns: tuple[str, str]) -> np.ndarray:
    df = pd.read_csv(path, usecols=list(columns)).dropna()
    return df[list(columns)].to_numpy(dtype=float)


def load_true_trajectories(trajectory_dir: Path, zebras: list[int]) -> dict[int, np.ndarray]:
    trajectories = {}
    for zebra_id in zebras:
        path = trajectory_dir / f"zebra_{zebra_id:03d}.csv"
        if not path.exists():
            raise FileNotFoundError(f"Missing real trajectory: {path}")
        trajectories[zebra_id] = read_xy_csv(path, ("x", "y"))
    return trajectories


def collect_files(args: argparse.Namespace) -> dict[tuple[int, str], list[EstimationFile]]:
    wanted_zebras = set(args.zebras)
    wanted_neighbors = set(args.neighbors)
    grouped: dict[tuple[int, str], list[EstimationFile]] = {}

    for path in args.data_dir.iterdir():
        parsed = parse_estimation_file(path)
        if parsed is None:
            continue
        if parsed.zebra_id not in wanted_zebras:
            continue
        if parsed.neighbors not in wanted_neighbors:
            continue

        match = ESTIMATION_PATTERN.match(path.name)
        if match.group("error") != args.error_on_position:
            continue

        grouped.setdefault((parsed.neighbors, parsed.seed), []).append(parsed)

    if not grouped:
        raise FileNotFoundError(
            f"No fixedSensorsNB estimation files found in {args.data_dir} "
            f"for neighbors={sorted(wanted_neighbors)}"
        )

    for files in grouped.values():
        files.sort(key=lambda item: (item.zebra_id, item.node_id))
    return grouped


def seed_rmse_curve(
    files: list[EstimationFile],
    true_trajectories: dict[int, np.ndarray],
    max_time_step: int,
) -> np.ndarray:
    squared_errors = np.full((len(files), max_time_step), np.nan, dtype=float)

    for row, item in enumerate(files):
        estimated = read_xy_csv(item.path, ("estimatedX", "estimatedY"))
        true = true_trajectories[item.zebra_id]
        samples = min(len(estimated), len(true), max_time_step)
        if samples == 0:
            continue
        squared_errors[row, :samples] = np.sum(
            (estimated[:samples] - true[:samples]) ** 2,
            axis=1,
        )

    with np.errstate(invalid="ignore"):
        return np.sqrt(np.nanmean(squared_errors, axis=0))


def compute_curves(
    grouped_files: dict[tuple[int, str], list[EstimationFile]],
    true_trajectories: dict[int, np.ndarray],
    neighbors: list[int],
    max_time_step: int,
) -> dict[int, tuple[np.ndarray, np.ndarray, int]]:
    curves_by_neighbors: dict[int, list[np.ndarray]] = {n: [] for n in neighbors}

    for (neighbors_value, _seed), files in sorted(
        grouped_files.items(),
        key=lambda item: (item[0][0], float(item[0][1])),
    ):
        if neighbors_value not in curves_by_neighbors:
            continue
        curves_by_neighbors[neighbors_value].append(
            seed_rmse_curve(files, true_trajectories, max_time_step)
        )

    summary = {}
    for neighbors_value, curves in curves_by_neighbors.items():
        if not curves:
            continue
        matrix = np.vstack(curves)
        with np.errstate(invalid="ignore"):
            mean = np.nanmean(matrix, axis=0)
            variance = (
                np.nanvar(matrix, axis=0, ddof=1)
                if len(curves) > 1
                else np.zeros_like(mean)
            )
        summary[neighbors_value] = (mean, variance, len(curves))
    return summary


def write_summary_csv(
    curves: dict[int, tuple[np.ndarray, np.ndarray, int]],
    output_dir: Path,
) -> Path:
    rows = []
    for neighbors, (mean, variance, seed_count) in curves.items():
        for time_step, (mean_value, variance_value) in enumerate(zip(mean, variance)):
            rows.append(
                {
                    "neighbors": neighbors,
                    "time_step": time_step,
                    "rmse_mean": mean_value,
                    "rmse_variance": variance_value,
                    "seed_count": seed_count,
                }
            )
    path = output_dir / "rmse_over_time_by_number_of_neighbors.csv"
    pd.DataFrame(rows).to_csv(path, index=False)
    return path


def plot_curves(
    curves: dict[int, tuple[np.ndarray, np.ndarray, int]],
    output_dir: Path,
    filename: str,
    rolling_window: int,
) -> tuple[Path, Path]:
    deep_colors = sns.color_palette("deep", n_colors=len(curves))
    palette = {neighbors: deep_colors[index] for index, neighbors in enumerate(curves)}

    fig, ax = plt.subplots(figsize=(9.2, 5.4))
    for index, (neighbors, (mean, variance, _seed_count)) in enumerate(curves.items()):
        if rolling_window > 1:
            mean = (
                pd.Series(mean)
                .rolling(rolling_window, center=True, min_periods=1)
                .mean()
                .to_numpy()
            )
            variance = (
                pd.Series(variance)
                .rolling(rolling_window, center=True, min_periods=1)
                .mean()
                .to_numpy()
            )
        time_steps = np.arange(len(mean))
        valid = np.isfinite(mean) & (mean > 0)
        if not valid.any():
            continue

        color = palette[neighbors]
        lower = np.maximum(mean - variance, 1e-3)
        upper = mean + variance

        ax.plot(
            time_steps[valid],
            mean[valid],
            color=color,
            linewidth=2.5,
            label=str(neighbors),
            zorder=3,
        )
        ax.fill_between(
            time_steps[valid],
            lower[valid],
            upper[valid],
            color=color,
            alpha=0.35,
            linewidth=0,
            zorder=2,
        )

    ax.set_yscale("log")
    ax.set_xlabel("Time Step", fontsize=24)
    ax.set_ylabel("RMSE", fontsize=24)
    ax.tick_params(axis="both", labelsize=15)
    ax.grid(True, which="major", linestyle="--", alpha=0.6)
    ax.grid(True, which="minor", axis="y", linestyle=":", alpha=0.25)
    ax.legend(title="|N|", fontsize=15, title_fontsize=17, loc="center right", frameon=True)

    for spine in ax.spines.values():
        spine.set_visible(True)
        spine.set_color("black")
        spine.set_linewidth(1.0)

    output_dir.mkdir(parents=True, exist_ok=True)
    png_path = output_dir / f"{filename}.png"
    pdf_path = output_dir / f"{filename}.pdf"
    fig.tight_layout()
    fig.savefig(png_path, dpi=300, bbox_inches="tight")
    fig.savefig(pdf_path, bbox_inches="tight")
    plt.close(fig)
    return pdf_path, png_path


def main() -> None:
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    neighbors = sorted(set(args.neighbors))
    true_trajectories = load_true_trajectories(args.trajectory_dir, list(args.zebras))
    grouped_files = collect_files(args)
    curves = compute_curves(grouped_files, true_trajectories, neighbors, args.max_time_step)

    for neighbors_value, (mean, variance, seed_count) in curves.items():
        finite = np.isfinite(mean)
        print(
            f"|N|={neighbors_value}: seeds={seed_count}, "
            f"time_steps={int(finite.sum())}, "
            f"first={mean[finite][0]:.3f}, last={mean[finite][-1]:.3f}, "
            f"last_variance={variance[finite][-1]:.3f}"
        )

    csv_path = write_summary_csv(curves, args.output_dir)
    pdf_path, png_path = plot_curves(
        curves,
        args.output_dir,
        args.filename,
        args.rolling_window,
    )
    print(f"saved {csv_path}")
    print(f"saved {pdf_path}")
    print(f"saved {png_path}")


if __name__ == "__main__":
    main()
