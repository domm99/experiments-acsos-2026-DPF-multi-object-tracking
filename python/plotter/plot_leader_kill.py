from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
import numpy as np
import pandas as pd


DEFAULT_ZEBRAS = (35, 37, 38)
DEFAULT_NODES = (11, 18)


@dataclass(frozen=True)
class ZebraSeries:
    zebra_id: int
    real: pd.DataFrame
    original_real_length: int
    estimated: pd.DataFrame
    split_step: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Plot the three fixedSensorsLB zebra trajectories, stitching the "
            "first leader estimations with the second leader estimations."
        )
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
        default=Path("data/fixedSensorsLB"),
        help="Directory containing fixedSensorsLB estimation CSV files.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("charts/fixedSensorsLB"),
        help="Directory where the plot is written.",
    )
    parser.add_argument(
        "--filename",
        default="three_zebras_fc_failure",
        help="Output filename stem.",
    )
    parser.add_argument(
        "--zebras",
        nargs="+",
        type=int,
        default=DEFAULT_ZEBRAS,
        help="Zebra IDs to plot.",
    )
    parser.add_argument(
        "--nodes",
        nargs=2,
        type=int,
        default=DEFAULT_NODES,
        metavar=("FIRST_NODE", "SECOND_NODE"),
        help="Leader nodes to stitch in chronological order.",
    )
    parser.add_argument("--n", type=int, default=0, help="Number-of-neighbors value in filenames.")
    parser.add_argument("--seed", default="0.0", help="Seed value in filenames.")
    parser.add_argument(
        "--error-on-position",
        default="0.0",
        help="errorOnPosition value in filenames.",
    )
    parser.add_argument(
        "--colorbar-max",
        type=int,
        default=1400,
        help="Fixed maximum value shown on the time-step colorbar.",
    )
    parser.add_argument(
        "--failure-time-step",
        type=int,
        default=700,
        help="Time step used to mark the leader failure in the plot and colorbar.",
    )
    return parser.parse_args()


def read_real_trajectory(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path).dropna(subset=["x", "y"])
    return df.reset_index(drop=True)


def read_estimation(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path).dropna(subset=["estimatedX", "estimatedY"])
    return df.reset_index(drop=True)


def estimation_path(
    data_dir: Path,
    zebra_id: int,
    node_id: int,
    number_of_neighbors: int,
    error_on_position: str,
    seed: str,
) -> Path:
    return data_dir / (
        f"estimations_zebra{zebra_id}_node-{node_id}_n-{number_of_neighbors}"
        f"_errorOnPosition-{error_on_position}_seed-{seed}.csv"
    )


def load_zebra_series(args: argparse.Namespace, zebra_id: int) -> ZebraSeries:
    real_path = args.trajectory_dir / f"zebra_{zebra_id:03d}.csv"
    if not real_path.exists():
        raise FileNotFoundError(f"Missing real trajectory: {real_path}")
    real = read_real_trajectory(real_path)

    parts = []
    offset = 0
    split_step = 0
    for index, node_id in enumerate(args.nodes):
        path = estimation_path(
            args.data_dir,
            zebra_id,
            node_id,
            args.n,
            args.error_on_position,
            args.seed,
        )
        if not path.exists():
            raise FileNotFoundError(f"Missing estimation CSV: {path}")
        part = read_estimation(path)
        part = part.assign(
            time_step=np.arange(offset, offset + len(part)),
            source_node=node_id,
        )
        parts.append(part)
        offset += len(part)
        if index == 0:
            split_step = offset

    estimated = pd.concat(parts, ignore_index=True)
    original_real_length = len(real)
    real = real.iloc[: min(len(real), len(estimated))].reset_index(drop=True)
    return ZebraSeries(
        zebra_id=zebra_id,
        real=real,
        original_real_length=original_real_length,
        estimated=estimated,
        split_step=split_step,
    )


def axis_limits(series: list[ZebraSeries]) -> tuple[tuple[float, float], tuple[float, float]]:
    x_values = []
    y_values = []
    for item in series:
        x_values.extend(item.real["x"].to_numpy())
        y_values.extend(item.real["y"].to_numpy())
        x_values.extend(item.estimated["estimatedX"].to_numpy())
        y_values.extend(item.estimated["estimatedY"].to_numpy())

    min_x, max_x = np.nanmin(x_values), np.nanmax(x_values)
    min_y, max_y = np.nanmin(y_values), np.nanmax(y_values)
    pad_x = max((max_x - min_x) * 0.08, 5.0)
    pad_y = max((max_y - min_y) * 0.08, 5.0)
    return (min_x - pad_x, max_x + pad_x), (min_y - pad_y, max_y + pad_y)


def plot(series: list[ZebraSeries], args: argparse.Namespace) -> tuple[Path, Path]:
    args.output_dir.mkdir(parents=True, exist_ok=True)

    colorbar_max = args.colorbar_max
    fig, ax = plt.subplots(figsize=(10, 9))

    real_colors = ["#666666", "#333333", "#999999"]
    last_scatter = None

    for index, item in enumerate(series):
        real = item.real
        estimated = item.estimated
        zebra_label = f"Z{item.zebra_id}"

        ax.plot(
            real["x"],
            real["y"],
            color=real_colors[index % len(real_colors)],
            linestyle="--",
            linewidth=2.3,
            alpha=0.65,
            zorder=2,
        )

        last_scatter = ax.scatter(
            estimated["estimatedX"],
            estimated["estimatedY"],
            c=estimated["time_step"],
            cmap="viridis",
            vmin=0,
            vmax=colorbar_max,
            s=18,
            alpha=0.82,
            edgecolors="none",
            zorder=4,
        )

        ax.scatter(
            real["x"].iloc[0],
            real["y"].iloc[0],
            color="green",
            s=130,
            zorder=5,
            edgecolors="black",
        )
        ax.scatter(
            real["x"].iloc[-1],
            real["y"].iloc[-1],
            color="red",
            s=130,
            zorder=5,
            edgecolors="black",
        )

        failure_index = min(max(args.failure_time_step, 0), len(real) - 1)
        failure_x = real["x"].iloc[failure_index]
        failure_y = real["y"].iloc[failure_index]
        ax.scatter(
            failure_x,
            failure_y,
            marker="x",
            color="red",
            s=420,
            linewidths=5.2,
            zorder=6,
        )

        ax.annotate(
            zebra_label,
            xy=(real["x"].iloc[-1], real["y"].iloc[-1]),
            xytext=(6, 6),
            textcoords="offset points",
            fontsize=13,
            fontweight="bold",
            color="#333333",
        )

    xlim, ylim = axis_limits(series)
    ax.set_xlim(*xlim)
    ax.set_ylim(*ylim)
    ax.set_xlabel("X (m)", fontsize=25)
    ax.set_ylabel("Y (m)", fontsize=25)
    ax.grid(True, linestyle="--", alpha=0.55)
    ax.set_aspect("equal", adjustable="box")
    ax.tick_params(labelsize=18)

    legend_handles = [
        Line2D([0], [0], color="#666666", linestyle="--", linewidth=3, label="Real Trajectory"),
        Line2D(
            [0],
            [0],
            marker="x",
            color="red",
            markersize=22,
            markeredgewidth=5.2,
            linestyle="none",
            label="FC Failure",
        ),
        Line2D(
            [0],
            [0],
            marker="o",
            color="none",
            markerfacecolor="#6c2c84",
            markersize=9,
            label="Estimated",
        ),
        Line2D(
            [0],
            [0],
            marker="o",
            color="black",
            markerfacecolor="green",
            markersize=9,
            linestyle="none",
            label="Start",
        ),
        Line2D(
            [0],
            [0],
            marker="o",
            color="black",
            markerfacecolor="red",
            markersize=9,
            linestyle="none",
            label="End",
        ),
    ]
    fig.legend(
        handles=legend_handles,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.98),
        ncol=3,
        fontsize=17,
        frameon=True,
        columnspacing=1.8,
        handlelength=2.2,
    )
    fig.subplots_adjust(top=0.8, right=0.84)

    if last_scatter is not None:
        cbar = fig.colorbar(last_scatter, ax=ax, orientation="vertical", fraction=0.046, pad=0.04)
        cbar.set_label("Time Steps", fontsize=22)
        cbar.ax.axhline(args.failure_time_step, color="red", linewidth=5.0)
        cbar.set_ticks([0, args.failure_time_step, colorbar_max])
        cbar.set_ticklabels(["0", str(args.failure_time_step), str(colorbar_max)])
        cbar.ax.tick_params(labelsize=18)

    pdf_path = args.output_dir / f"{args.filename}.pdf"
    png_path = args.output_dir / f"{args.filename}.png"
    fig.savefig(pdf_path, bbox_inches="tight")
    fig.savefig(png_path, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return pdf_path, png_path


def main() -> None:
    args = parse_args()
    series = [load_zebra_series(args, zebra_id) for zebra_id in args.zebras]

    for item in series:
        print(
            f"zebra {item.zebra_id}: real={item.original_real_length}, "
            f"plotted_real={len(item.real)}, "
            f"node-{args.nodes[0]}={item.split_step}, "
            f"node-{args.nodes[1]}={len(item.estimated) - item.split_step}, "
            f"stitched={len(item.estimated)}"
        )
        if item.original_real_length != len(item.estimated):
            print(
                f"{item.original_real_length - len(item.estimated)} points"
            )

    pdf_path, png_path = plot(series, args)
    print(f"saved {pdf_path}")
    print(f"saved {png_path}")


if __name__ == "__main__":
    main()
