import argparse
import os
import re
from pathlib import Path

import numpy as np
import pandas as pd


MPLCONFIGDIR = Path("charts/.matplotlib").resolve()
MPLCONFIGDIR.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(MPLCONFIGDIR))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns


ESTIMATION_PATTERN = re.compile(
    r"estimations_zebra(?P<zebra>\d+)_node-(?P<node>\d+)_n-(?P<neighbors>\d+)"
    r"_errorOnPosition-(?P<error_on_position>-?\d+(?:\.\d+)?)"
    r"_seed-(?P<seed>-?\d+(?:\.\d+)?)\.csv$"
)


def parse_args():
    parser = argparse.ArgumentParser(
        description="Plot trajectory RMSE against errorOnPosition for movingSensorsNB."
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("data/movingSensorsNB"),
        help="Directory containing exported estimation CSV files.",
    )
    parser.add_argument(
        "--trajectory-dir",
        type=Path,
        default=Path("src/main/resources/zebras-trajectories/flights/flight_1_zebras"),
        help="Directory containing true zebra trajectory CSV files.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("charts/movingSensorsNB"),
        help="Directory where charts and summary CSV files will be written.",
    )
    parser.add_argument(
        "--zebras",
        type=int,
        nargs="+",
        default=[35, 37, 38],
        help="Zebra IDs to include in the aggregate RMSE.",
    )
    parser.add_argument(
        "--x-axis",
        choices=["parameter", "euclidean"],
        default="parameter",
        help=(
            "Use the raw errorOnPosition parameter or the Euclidean displacement "
            "sqrt(errorOnPosition^2 + errorOnPosition^2)."
        ),
    )
    return parser.parse_args()


def read_true_trajectory(trajectory_dir, zebra):
    path = trajectory_dir / f"zebra_{zebra:03d}.csv"
    if not path.exists():
        raise FileNotFoundError(f"True trajectory not found: {path}")
    return pd.read_csv(path, usecols=["x", "y"]).dropna()


def read_estimation(path):
    return pd.read_csv(path, usecols=["estimatedX", "estimatedY"]).dropna()


def trajectory_rmse(true_df, estimation_df):
    samples = min(len(true_df), len(estimation_df))
    if samples == 0:
        return np.nan, 0

    true_xy = true_df.iloc[:samples][["x", "y"]].to_numpy(dtype=float)
    estimated_xy = estimation_df.iloc[:samples][["estimatedX", "estimatedY"]].to_numpy(
        dtype=float
    )
    squared_distance = np.sum((estimated_xy - true_xy) ** 2, axis=1)
    return float(np.sqrt(np.mean(squared_distance))), samples


def collect_file_metrics(data_dir, trajectory_dir, zebras):
    true_trajectories = {
        zebra: read_true_trajectory(trajectory_dir, zebra) for zebra in zebras
    }
    allowed_zebras = set(zebras)
    rows = []

    for path in sorted(data_dir.glob("*.csv")):
        match = ESTIMATION_PATTERN.match(path.name)
        if not match:
            continue

        metadata = match.groupdict()
        zebra = int(metadata["zebra"])
        if zebra not in allowed_zebras:
            continue

        rmse, samples = trajectory_rmse(
            true_trajectories[zebra], read_estimation(path)
        )
        rows.append(
            {
                "zebra": zebra,
                "node": int(metadata["node"]),
                "neighbors": int(metadata["neighbors"]),
                "error_on_position": float(metadata["error_on_position"]),
                "seed": float(metadata["seed"]),
                "rmse": rmse,
                "samples": samples,
                "file": str(path),
            }
        )

    if not rows:
        raise RuntimeError(f"No estimation CSV files found in {data_dir}")

    return pd.DataFrame(rows).dropna(subset=["rmse"])


def summarize_metrics(file_metrics):
    zebra_seed_metrics = (
        file_metrics.groupby(["error_on_position", "seed", "zebra"], as_index=False)
        .agg(
            rmse=("rmse", "mean"),
            node_count=("node", "nunique"),
            samples_min=("samples", "min"),
            samples_max=("samples", "max"),
        )
        .sort_values(["error_on_position", "seed", "zebra"])
    )

    seed_metrics = (
        zebra_seed_metrics.groupby(["error_on_position", "seed"], as_index=False)
        .agg(
            rmse=("rmse", "mean"),
            zebra_count=("zebra", "nunique"),
            mean_node_count=("node_count", "mean"),
            min_samples=("samples_min", "min"),
            max_samples=("samples_max", "max"),
        )
        .sort_values(["error_on_position", "seed"])
    )

    summary = (
        seed_metrics.groupby("error_on_position", as_index=False)
        .agg(
            rmse_mean=("rmse", "mean"),
            rmse_variance=("rmse", lambda x: x.var(ddof=1) if len(x) > 1 else 0.0),
            seed_count=("seed", "nunique"),
            zebra_count=("zebra_count", "min"),
            mean_node_count=("mean_node_count", "mean"),
        )
        .sort_values("error_on_position")
    )
    summary["rmse_variance"] = summary["rmse_variance"].fillna(0.0)
    summary["rmse_lower"] = np.maximum(
        summary["rmse_mean"] - summary["rmse_variance"], 0.0
    )
    summary["rmse_upper"] = summary["rmse_mean"] + summary["rmse_variance"]
    summary["desired_position_error_distance"] = (
        np.sqrt(2.0) * summary["error_on_position"]
    )

    return zebra_seed_metrics, seed_metrics, summary


def plot_summary(summary, output_dir, x_axis):
    plt.rcdefaults()
    sns.set_theme(
        context="notebook",
        style="white",
        rc={
            "axes.spines.right": True,
            "axes.spines.top": True,
            "axes.titlesize": 25,
            "axes.labelsize": 25,
            "xtick.labelsize": 22,
            "ytick.labelsize": 22,
            "font.family": "DejaVu Sans",
        },
    )

    fig, ax = plt.subplots(figsize=(8.8, 5.2))
    color = "#2a9d8f"
    x_column = (
        "desired_position_error_distance"
        if x_axis == "euclidean"
        else "error_on_position"
    )
    x_label = (
        "Errore euclideo sulla posizione desiderata (m)"
        if x_axis == "euclidean"
        else "Error on Tracked Position (m)"
    )

    sns.lineplot(
        data=summary,
        x=x_column,
        y="rmse_mean",
        marker="o",
        markersize=12,
        linewidth=5,
        color=color,
        ax=ax,
    )

    ax.errorbar(
        summary[x_column],
        summary["rmse_mean"],
        yerr=np.sqrt(summary["rmse_variance"]),
        fmt="none",
        ecolor=color,
        elinewidth=2,
        capsize=5,
        alpha=0.8,
    )

    ax.set_title("", pad=16, weight="bold")
    ax.set_xlabel(x_label)
    ax.set_ylabel("RMSE (m)")
    x = summary[x_column].to_numpy(dtype=float)
    ax.set_xticks(x)
    margin = (x.max() - x.min()) * 0.06 if x.max() > x.min() else 1.0
    ax.set_xlim(x.min() - margin, x.max() + margin)
    ax.set_ylim(bottom=0)
    ax.grid(
        True,
        which="major",
        axis="both",
        linestyle="--",
        color="#bdbdbd",
        alpha=0.75,
    )

    for spine in ax.spines.values():
        spine.set_visible(True)
        spine.set_color("black")
        spine.set_linewidth(1.0)

    fig.tight_layout()
    output_dir.mkdir(parents=True, exist_ok=True)
    suffix = "euclidean_error" if x_axis == "euclidean" else "error_on_position"
    png_path = output_dir / f"rmse_by_{suffix}.png"
    pdf_path = output_dir / f"rmse_by_{suffix}.pdf"
    fig.savefig(png_path, dpi=300, bbox_inches="tight")
    fig.savefig(pdf_path, bbox_inches="tight")
    plt.close(fig)
    return png_path, pdf_path


def main():
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    file_metrics = collect_file_metrics(args.data_dir, args.trajectory_dir, args.zebras)
    zebra_seed_metrics, seed_metrics, summary = summarize_metrics(file_metrics)

    file_metrics.to_csv(args.output_dir / "rmse_file_metrics.csv", index=False)
    zebra_seed_metrics.to_csv(
        args.output_dir / "rmse_zebra_seed_metrics.csv", index=False
    )
    seed_metrics.to_csv(args.output_dir / "rmse_seed_metrics.csv", index=False)
    summary.to_csv(args.output_dir / "rmse_by_error_on_position.csv", index=False)

    png_path, pdf_path = plot_summary(summary, args.output_dir, args.x_axis)
    print(f"Wrote {png_path}")
    print(f"Wrote {pdf_path}")
    print(summary.to_string(index=False))


if __name__ == "__main__":
    main()
