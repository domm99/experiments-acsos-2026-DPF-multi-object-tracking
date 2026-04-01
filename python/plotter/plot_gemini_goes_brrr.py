import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path
import glob
import re
from collections import defaultdict

def read_real_trajectory(path):
    # Skip the Alchemist header and manually assign column names
    df = pd.read_csv(path, skiprows=1, names=['x', 'y', 'timestamp'])
    return df

def read_estimation(path):
    # Skip the Alchemist header and manually assign column names
    df = pd.read_csv(path, skiprows=1, names=['estimatedX', 'estimatedY'])
    return df

def generate_charts(plot_configs, charts_path):

    # Pre-defined high-contrast colors for the real trajectory lines
    line_colors = ['black', 'gray', 'blue', 'red', 'green', 'purple', 'orange', 'cyan', 'magenta', 'brown']

    for title, entities in plot_configs.items():
        fig, ax = plt.subplots(figsize=(10, 10), constrained_layout=True)

        # Calculate global MIN and MAX time steps to synchronize the colormap across all zebras
        min_time = float('inf')
        max_time = float('-inf')
        for entity in entities:
            df_est = entity['estimation']
            min_time = min(min_time, df_est.index.min())
            max_time = max(max_time, df_est.index.max())

        # Initialize bounding box for axis limits
        min_x, max_x = float('inf'), float('-inf')
        min_y, max_y = float('inf'), float('-inf')

        last_scatter = None

        # Plot each zebra's real and aggregated estimation trajectories
        for idx, entity in enumerate(entities):
            df_true = entity['real']
            df_est = entity['estimation']
            zebra_name = entity['name']

            # Plot real trajectory (dashed line)
            ax.plot(df_true['x'], df_true['y'],
                    label=f'Real {zebra_name}',
                    color=line_colors[idx % len(line_colors)],
                    linestyle='--', linewidth=2, alpha=0.5)

            # Plot estimated points (scatter)
            last_scatter = ax.scatter(
                df_est['estimatedX'],
                df_est['estimatedY'],
                c=df_est.index,
                cmap='viridis',
                vmin=min_time,
                vmax=max_time,
                s=20,
                alpha=0.8,
                zorder=4,
                label=f'Estimation {zebra_name}'
            )

            # Mark start and end points
            start_label = 'Start' if idx == 0 else None
            end_label = 'End' if idx == 0 else None

            ax.scatter(df_true['x'].iloc[0], df_true['y'].iloc[0],
                       color='green', s=150, zorder=5, edgecolors='black', label=start_label)

            ax.scatter(df_true['x'].iloc[-1], df_true['y'].iloc[-1],
                       color='red', s=150, zorder=5, edgecolors='black', label=end_label)

            # Update bounds
            min_x = min(min_x, df_true['x'].min(), df_est['estimatedX'].min())
            max_x = max(max_x, df_true['x'].max(), df_est['estimatedX'].max())
            min_y = min(min_y, df_true['y'].min(), df_est['estimatedY'].min())
            max_y = max(max_y, df_true['y'].max(), df_est['estimatedY'].max())

        # Set plot cosmetics
        ax.set_title(title, fontsize=35)
        ax.set_xlabel('X (m)', fontsize=25)
        ax.set_ylabel('Y (m)', fontsize=25)

        # Apply a 10% padding to bounds
        padding_x = (max_x - min_x) * 0.1 if max_x != min_x else 10
        padding_y = (max_y - min_y) * 0.1 if max_y != min_y else 10
        ax.set_xlim(min_x - padding_x, max_x + padding_x)
        ax.set_ylim(min_y - padding_y, max_y + padding_y)

        ax.grid(True, linestyle='--', alpha=0.6)
        ax.set_aspect('equal', adjustable='box')
        ax.tick_params(labelsize=20)

        # Handle Legend creation (avoiding duplicates)
        handles, labels = ax.get_legend_handles_labels()
        by_label = dict(zip(labels, handles))
        leg = ax.legend(by_label.values(), by_label.keys(), loc='upper center', bbox_to_anchor=(0.5, -0.15),
                        ncol=4, fontsize=15)

        for handle in leg.legend_handles:
            if hasattr(handle, 'set_sizes'):
                handle.set_sizes([100])
            elif hasattr(handle, 'set_linewidth'):
                handle.set_linewidth(3)

        # Draw synchronous Colorbar
        if last_scatter:
            cbar = fig.colorbar(last_scatter, ax=ax, orientation='vertical', fraction=0.046, pad=0.04)
            cbar.set_label('Time Steps', fontsize=20)
            cbar.ax.tick_params(labelsize=15)
            cbar.set_ticks([min_time, max_time])
            cbar.set_ticklabels([str(min_time), str(max_time)])

        # Save to PNG and PDF formats
        # Ensure safe filenames by replacing spaces and parentheses
        safe_filename = title.replace(" ", "_").replace("(", "").replace(")", "").replace(",", "").lower()
        plt.savefig(f'{charts_path}/{safe_filename}.pdf', bbox_inches='tight')
        plt.savefig(f'{charts_path}/{safe_filename}.png', bbox_inches='tight', dpi=300)
        plt.close()

if __name__ == '__main__':

    # ---------------------------------------------------------
    # QUICK CONFIGURATION AREA
    # ---------------------------------------------------------
    flight_number = 1
    zebra_ids = [35, 38]

    # Define the list of experiment names. These must match the subfolder names inside "data/"
    experiments = ['oneFixedSensorOneZebra']

    base_charts_path = 'charts'
    base_data_path = 'data'
    real_base_path = f'src/main/resources/zebras-trajectories/flights/flight_{flight_number}_zebras'
    # ---------------------------------------------------------

    # Setup regex to extract parameters from filenames
    # Example filename: estimations_zebra35_node-10_n-0_seed-42.0.csv
    file_pattern = re.compile(r'estimations_zebra(\d+)_node-(\d+)_n-(\d+)_seed-([\d\.]+)\.csv')

    # Iterate over each defined experiment
    for current_experiment in experiments:
        print(f"\n--- Processing Experiment: {current_experiment} ---")

        # Dynamically define paths for the current experiment
        exp_data_path = f'{base_data_path}/{current_experiment}'
        exp_charts_path = f'{base_charts_path}/{current_experiment}'

        # Check if the data directory for this experiment exists
        if not Path(exp_data_path).exists():
            print(f"Warning: Directory '{exp_data_path}' does not exist. Skipping...")
            continue

        # Create a specific output folder for the charts of this experiment
        Path(exp_charts_path).mkdir(parents=True, exist_ok=True)

        # Discover all estimation files inside the experiment's data folder
        all_files = glob.glob(f'{exp_data_path}/estimations_zebra*_node-*_n-*_seed-*.csv')

        if not all_files:
            print(f"No estimation files found in '{exp_data_path}'. Skipping...")
            continue

        # Group files by (zebra_id, node, n) to aggregate across multiple seeds
        grouped_files = defaultdict(list)

        for file_path in all_files:
            # Extract filename from path
            filename = Path(file_path).name
            match = file_pattern.search(filename)

            if match:
                zid, node, n, seed = match.groups()
                zid, node, n = int(zid), int(node), int(n)

                # Only process zebras we are interested in
                if zid in zebra_ids:
                    grouped_files[(zid, node, n)].append(file_path)

        # Re-organize configurations by (node, n) so we can plot multiple zebras together
        scenarios = defaultdict(list)

        for (zid, node, n), files in grouped_files.items():
            dfs = []
            for f in files:
                dfs.append(read_estimation(f))

            # Aggregate multiple seeds by taking the mean across the index (time steps)
            df_estimation_aggregated = pd.concat(dfs).groupby(level=0).mean()

            # Load the real trajectory (handling the padding with zeros like zebra_035.csv)
            z_str = str(zid).zfill(3)
            real_path = f'{real_base_path}/zebra_{z_str}.csv'
            df_real = read_real_trajectory(real_path)

            entity = {
                'name': f'Zebra {zid}',
                'real': df_real,
                'estimation': df_estimation_aggregated
            }

            scenarios[(node, n)].append((zid, entity))

        # Generate configurations for isolated and combined plots
        plot_configs = {}

        for (node, n), entities_list in scenarios.items():

            combined_entities = []

            for zid, entity in entities_list:
                # 1. Configuration for isolated plot
                title = f'Zebra {zid} Node {node} N {n}'
                plot_configs[title] = [entity]
                combined_entities.append(entity)

            # 2. Configuration for combined plot (if more than 1 zebra is present in this scenario)
            if len(combined_entities) > 1:
                combined_title = f'Combined Trajectories Node {node} N {n}'
                plot_configs[combined_title] = combined_entities

        if not plot_configs:
            print(f"No matching data found in '{exp_data_path}' for Zebras: {zebra_ids}")
        else:
            # Generate all charts for the current experiment
            generate_charts(plot_configs, exp_charts_path)
            print(f"Charts for '{current_experiment}' successfully generated in '{exp_charts_path}'.")

    print("\nAll experiments processed.")