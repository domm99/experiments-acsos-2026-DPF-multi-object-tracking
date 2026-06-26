from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
import numpy as np
import pandas as pd


DEFAULT_ZEBRAS = (35, 37, 38)
DEFAULT_NEIGHBORS = [7]
ESTIMATION_PATTERN = re.compile(
    r"estimations_zebra(?P<zebra>\d+)_node-(?P<node>\d+)_n-(?P<neighbors>\d+)"
    r"_errorOnPosition-(?P<error>[\d.]+)_seed-(?P<seed>[\d.]+)\.csv$"
)


@dataclass(frozen=True)
class ZebraEstimate:
    zebra_id: int
    real: pd.DataFrame
    original_real_length: int
    estimated: pd.DataFrame
    file_count: int


@dataclass(frozen=True)
class NeighborPanel:
    neighbors: int
    zebras: list[ZebraEstimate]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Plot fixedSensorsNB estimates for the usual three zebras, with "
            "one subplot for each discovered numberOfNeighbors value."
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
        default="three_zebras_by_number_of_neighbors",
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
        "--neighbors",
        nargs="+",
        type=int,
        default=DEFAULT_NEIGHBORS,
        help="Neighbor-count values to plot.",
    )
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
    return parser.parse_args()


def read_real_trajectory(path: Path) -> pd.DataFrame:
    return pd.read_csv(path).dropna(subset=["x", "y"]).reset_index(drop=True)


def read_estimation(path: Path) -> pd.DataFrame:
    return pd.read_csv(path).dropna(subset=["estimatedX", "estimatedY"])


def parse_estimation_filename(path: Path) -> dict[str, str] | None:
    match = ESTIMATION_PATTERN.match(path.name)
    return match.groupdict() if match else None


def discover_neighbors(args: argparse.Namespace) -> list[int]:
    if args.neighbors:
        return sorted(set(args.neighbors))

    zebras = set(args.zebras)
    neighbors = set()
    for path in args.data_dir.iterdir():
        parsed = parse_estimation_filename(path)
        if not parsed:
            continue
        if int(parsed["zebra"]) not in zebras:
            continue
        if parsed["seed"] != args.seed or parsed["error"] != args.error_on_position:
            continue
        neighbors.add(int(parsed["neighbors"]))

    if not neighbors:
        raise FileNotFoundError(
            f"No estimation files found in {args.data_dir} for seed={args.seed} "
            f"and errorOnPosition={args.error_on_position}"
        )
    return sorted(neighbors)


def matching_estimation_files(
    data_dir: Path,
    zebra_id: int,
    neighbors: int,
    seed: str,
    error_on_position: str,
) -> list[Path]:
    files = []
    for path in data_dir.iterdir():
        parsed = parse_estimation_filename(path)
        if not parsed:
            continue
        if int(parsed["zebra"]) != zebra_id:
            continue
        if int(parsed["neighbors"]) != neighbors:
            continue
        if parsed["seed"] != seed or parsed["error"] != error_on_position:
            continue
        files.append(path)
    return sorted(files, key=lambda item: int(parse_estimation_filename(item)["node"]))


def aggregate_estimates(files: list[Path]) -> pd.DataFrame:
    if not files:
        raise FileNotFoundError("No estimation files to aggregate.")

    frames = [read_estimation(path) for path in files]
    aggregated = (
        pd.concat(frames)
        .groupby(level=0)[["estimatedX", "estimatedY"]]
        .mean()
        .sort_index()
    )
    aggregated["time_step"] = aggregated.index.to_numpy(dtype=int)
    return aggregated.reset_index(drop=True)


def load_panel(args: argparse.Namespace, neighbors: int) -> NeighborPanel:
    estimates = []
    for zebra_id in args.zebras:
        real_path = args.trajectory_dir / f"zebra_{zebra_id:03d}.csv"
        if not real_path.exists():
            raise FileNotFoundError(f"Missing real trajectory: {real_path}")

        real = read_real_trajectory(real_path)
        files = matching_estimation_files(
            args.data_dir,
            zebra_id,
            neighbors,
            args.seed,
            args.error_on_position,
        )
        estimated = aggregate_estimates(files)

        horizon = min(len(real), int(estimated["time_step"].max()) + 1)
        plotted_real = real.iloc[:horizon].reset_index(drop=True)
        estimates.append(
            ZebraEstimate(
                zebra_id=zebra_id,
                real=plotted_real,
                original_real_length=len(real),
                estimated=estimated,
                file_count=len(files),
            )
        )
    return NeighborPanel(neighbors=neighbors, zebras=estimates)


def axis_limits(panels: list[NeighborPanel]) -> tuple[tuple[float, float], tuple[float, float]]:
    x_values = []
    y_values = []
    for panel in panels:
        for zebra in panel.zebras:
            x_values.extend(zebra.real["x"].to_numpy())
            y_values.extend(zebra.real["y"].to_numpy())
            x_values.extend(zebra.estimated["estimatedX"].to_numpy())
            y_values.extend(zebra.estimated["estimatedY"].to_numpy())

    min_x, max_x = np.nanmin(x_values), np.nanmax(x_values)
    min_y, max_y = np.nanmin(y_values), np.nanmax(y_values)
    pad_x = max((max_x - min_x) * 0.06, 5.0)
    pad_y = max((max_y - min_y) * 0.06, 5.0)
    return (min_x - pad_x, max_x + pad_x), (min_y - pad_y, max_y + pad_y)


def plot(panels: list[NeighborPanel], args: argparse.Namespace) -> tuple[Path, Path]:
    args.output_dir.mkdir(parents=True, exist_ok=True)

    fig, axes = plt.subplots(
        1,
        len(panels),
        figsize=(4.1 * len(panels), 5.4),
        sharex=True,
        sharey=True,
    )
    if len(panels) == 1:
        axes = [axes]

    xlim, ylim = axis_limits(panels)
    last_scatter = None
    real_colors = ["#666666", "#444444", "#888888"]

    for ax, panel in zip(axes, panels):
        for index, zebra in enumerate(panel.zebras):
            real = zebra.real
            estimated = zebra.estimated

            ax.plot(
                real["x"],
                real["y"],
                color=real_colors[index % len(real_colors)],
                linestyle="--",
                linewidth=1.7,
                alpha=0.62,
                zorder=2,
            )
            last_scatter = ax.scatter(
                estimated["estimatedX"],
                estimated["estimatedY"],
                c=estimated["time_step"],
                cmap="viridis",
                vmin=0,
                vmax=args.colorbar_max,
                s=9,
                alpha=0.78,
                edgecolors="none",
                zorder=4,
            )
            ax.scatter(
                real["x"].iloc[0],
                real["y"].iloc[0],
                color="green",
                s=55,
                zorder=5,
                edgecolors="black",
                linewidths=0.7,
            )
            ax.scatter(
                real["x"].iloc[-1],
                real["y"].iloc[-1],
                color="red",
                s=55,
                zorder=5,
                edgecolors="black",
                linewidths=0.7,
            )
            ax.annotate(
                f"Z{zebra.zebra_id}",
                xy=(real["x"].iloc[-1], real["y"].iloc[-1]),
                xytext=(4, 4),
                textcoords="offset points",
                fontsize=9,
                fontweight="bold",
                color="#333333",
            )

        ax.set_title(f"|N| = {panel.neighbors}", fontsize=19)
        ax.set_xlim(*xlim)
        ax.set_ylim(*ylim)
        ax.set_aspect("equal", adjustable="box")
        ax.grid(True, linestyle="--", alpha=0.45)
        ax.set_xlabel("X (m)", fontsize=15)
        ax.tick_params(labelsize=13)

    axes[0].set_ylabel("Y (m)", fontsize=15)

    legend_handles = [
        Line2D([0], [0], color="#666666", linestyle="--", linewidth=2.2, label="Real Trajectory"),
        Line2D(
            [0],
            [0],
            marker="o",
            color="none",
            markerfacecolor="#6c2c84",
            markersize=7.5,
            label="Estimated",
        ),
        Line2D(
            [0],
            [0],
            marker="o",
            color="black",
            markerfacecolor="green",
            markersize=7.5,
            linestyle="none",
            label="Start",
        ),
        Line2D(
            [0],
            [0],
            marker="o",
            color="black",
            markerfacecolor="red",
            markersize=7.5,
            linestyle="none",
            label="End",
        ),
    ]
    fig.legend(
        handles=legend_handles,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.04),
        ncol=2,
        fontsize=15,
        frameon=True,
        columnspacing=2.1,
    )

    fig.subplots_adjust(top=0.82, left=0.06, right=0.84, bottom=0.22, wspace=0.17)

    if last_scatter is not None:
        cbar_ax = fig.add_axes([0.875, 0.28, 0.012, 0.50])
        cbar = fig.colorbar(last_scatter, cax=cbar_ax, orientation="vertical")
        cbar.set_label("Time Steps", fontsize=16)
        cbar.set_ticks([0, args.colorbar_max])
        cbar.set_ticklabels(["0", str(args.colorbar_max)])
        cbar.ax.tick_params(labelsize=13)

    pdf_path = args.output_dir / f"{args.filename}.pdf"
    png_path = args.output_dir / f"{args.filename}.png"
    #plt.tight_layout()
    fig.savefig(pdf_path, bbox_inches="tight", pad_inches=0.2)
    
    fig.savefig(png_path, dpi=300, bbox_inches="tight")
    plt.close(fig)
    return pdf_path, png_path


def main() -> None:
    args = parse_args()
    neighbors = [0, 1, 4, 7]
    print(f'AAAAAAAA {neighbors}')
    panels = [load_panel(args, neighbors_value) for neighbors_value in neighbors]

    for panel in panels:
        print(f"|N|={panel.neighbors}")
        for zebra in panel.zebras:
            print(
                f"  zebra {zebra.zebra_id}: files={zebra.file_count}, "
                f"real={zebra.original_real_length}, plotted_real={len(zebra.real)}, "
                f"estimated={len(zebra.estimated)}"
            )

    pdf_path, png_path = plot(panels, args)
    print(f"saved {pdf_path}")
    print(f"saved {png_path}")


if __name__ == "__main__":
    main()
