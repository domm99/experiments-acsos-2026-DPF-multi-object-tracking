import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path
import glob
import re
from collections import defaultdict

def read_real_trajectory(path):
    # Skip the Alchemist header and manually assign column names, then drop any NaN rows
    df = pd.read_csv(path, skiprows=1, names=['x', 'y', 'timestamp']).dropna()
    return df

def read_estimation(path):
    # Skip the Alchemist header and manually assign column names, then drop any NaN rows
    df = pd.read_csv(path, skiprows=1, names=['estimatedX', 'estimatedY']).dropna()
    return df

def generate_charts(plot_configs, charts_path):

    # Pre-defined high-contrast colors for the real trajectory lines
    line_colors = ['black', 'gray', 'blue', 'red', 'green', 'purple', 'orange', 'cyan', 'magenta', 'brown']

    for title, entities in plot_configs.items():
        fig, ax = plt.subplots(figsize=(10, 10))

        # Calculate global MIN and MAX time steps to synchronize the colormap across all zebras safely
        min_time = float('inf')
        max_time = float('-inf')

        for entity in entities:
            df_est = entity['estimation']
            if not df_est.empty:
                min_time = min(min_time, df_est.index.min())
                max_time = max(max_time, df_est.index.max())

        # If we couldn't find any valid time steps (e.g. empty estimations), default to 0 and 1
        if min_time == float('inf') or pd.isna(min_time):
            min_time, max_time = 0, 1

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
            if not df_true.empty:
                ax.plot(df_true['x'], df_true['y'],
                        label=f'Real {zebra_name}',
                        color=line_colors[idx % len(line_colors)],
                        linestyle='--', linewidth=2, alpha=0.5)

                # Mark start and end points
                start_label = 'Start' if idx == 0 else None
                end_label = 'End' if idx == 0 else None

                ax.scatter(df_true['x'].iloc[0], df_true['y'].iloc[0],
                           color='green', s=150, zorder=5, edgecolors='black', label=start_label)

                ax.scatter(df_true['x'].iloc[-1], df_true['y'].iloc[-1],
                           color='red', s=150, zorder=5, edgecolors='black', label=end_label)

            # Plot estimated points (scatter)
            if not df_est.empty:
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

            # --- Safely Update Bounds ---
            valid_x, valid_y = [], []

            if not df_true.empty:
                valid_x.extend([df_true['x'].min(), df_true['x'].max()])
                valid_y.extend([df_true['y'].min(), df_true['y'].max()])

            if not df_est.empty:
                valid_x.extend([df_est['estimatedX'].min(), df_est['estimatedX'].max()])
                valid_y.extend([df_est['estimatedY'].min(), df_est['estimatedY'].max()])

            # Filter out any lingering NaNs just in case
            valid_x = [v for v in valid_x if pd.notna(v)]
            valid_y = [v for v in valid_y if pd.notna(v)]

            if valid_x:
                min_x = min(min_x, min(valid_x))
                max_x = max(max_x, max(valid_x))
            if valid_y:
                min_y = min(min_y, min(valid_y))
                max_y = max(max_y, max(valid_y))

        # Set plot cosmetics
        ax.set_title(title, fontsize=35)
        ax.set_xlabel('X (m)', fontsize=25)
        ax.set_ylabel('Y (m)', fontsize=25)

        # Apply limits only if we have found valid bounds (not infinite)
        if min_x != float('inf') and max_x != float('-inf'):
            padding_x = (max_x - min_x) * 0.1 if max_x != min_x else 10
            padding_y = (max_y - min_y) * 0.1 if max_y != min_y else 10
            ax.set_xlim(min_x - padding_x, max_x + padding_x)
            ax.set_ylim(min_y - padding_y, max_y + padding_y)

        ax.grid(True, linestyle='--', alpha=0.6)
        ax.set_aspect('equal', adjustable='box')
        ax.tick_params(labelsize=20)

        # Handle Legend creation (avoiding duplicates)
        handles, labels = ax.get_legend_handles_labels()
        if handles:
            by_label = dict(zip(labels, handles))
            leg = ax.legend(by_label.values(), by_label.keys(), loc='upper center', bbox_to_anchor=(0.5, -0.15),
                            ncol=4, fontsize=15)

            for handle in leg.legend_handles:
                if hasattr(handle, 'set_sizes'):
                    handle.set_sizes([100])
                elif hasattr(handle, 'set_linewidth'):
                    handle.set_linewidth(3)

        # Draw synchronous Colorbar only if at least one estimation scatter was plotted
        if last_scatter:
            cbar = fig.colorbar(last_scatter, ax=ax, orientation='vertical', fraction=0.046, pad=0.04)
            cbar.set_label('Time Steps', fontsize=20)
            cbar.ax.tick_params(labelsize=15)
            cbar.set_ticks([min_time, max_time])
            cbar.set_ticklabels([str(round(min_time)), str(round(max_time))])

        # Apply tight layout to fix bounding issues before saving
        fig.tight_layout()

        # --- Directory Management ---
        # Determine if this is an isolated plot or a combined one
        if len(entities) == 1:
            # Create a dedicated subfolder for the single zebra
            zebra_folder_name = entities[0]['name'].replace(" ", "_")
            final_output_path = f"{charts_path}/{zebra_folder_name}"
        else:
            # Save combined plots directly in the main experiment charts folder
            final_output_path = charts_path

        Path(final_output_path).mkdir(parents=True, exist_ok=True)

        # Save to PNG and PDF formats
        # Ensure safe filenames by stripping LaTeX math symbols and replacing spaces
        safe_filename = title.replace(" ", "_").replace("$", "").replace("|", "").replace("=", "").replace("(", "").replace(")", "").replace(",", "").replace("-", "_").lower()

        plt.savefig(f'{final_output_path}/{safe_filename}.pdf', bbox_inches='tight')
        plt.savefig(f'{final_output_path}/{safe_filename}.png', bbox_inches='tight', dpi=300)
        plt.close(fig)

if __name__ == '__main__':

    # ---------------------------------------------------------
    # QUICK CONFIGURATION AREA
    # ---------------------------------------------------------
    flight_number = 1

    # Define the list of experiment names. These must match the subfolder names inside "data/"
    experiments = ['fixedSensorsNB', 'fixedSensorsLB', 'movingSensorsNB', 'movingSensorsLB']

    base_charts_path = 'charts'
    base_data_path = 'data'
    real_base_path = f'src/main/resources/zebras-trajectories/flights/flight_{flight_number}_zebras'
    # ---------------------------------------------------------

    # Setup regex to extract parameters from filenames
    # Example filename: estimations_zebra35_node-10_n-0_errorOnPosition-0.0_seed-42.0.csv
    file_pattern = re.compile(
        r'estimations_zebra(?P<zebra>\d+)_node-(?P<node>\d+)_n-(?P<n>\d+)'
        r'(?:_errorOnPosition-(?P<error_on_position>-?\d+(?:\.\d+)?))?'
        r'_seed-(?P<seed>-?\d+(?:\.\d+)?)\.csv$'
    )

    # Iterate over each defined experiment
    for current_experiment in experiments:
        print(f"\n--- Processing Experiment: {current_experiment} ---")

        # Check if this experiment is Leader Based (LB) or Neighbor Based (NB)
        is_lb_experiment = current_experiment.endswith('LB')

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

        # Group files by (zebra_id, n, error_on_position) so different YAML-driven
        # error settings are kept separate instead of being merged together.
        grouped_files = defaultdict(list)

        # Track discovered zebras just for logging purposes
        discovered_zebras = set()

        for file_path in all_files:
            # Extract filename from path
            filename = Path(file_path).name
            match = file_pattern.search(filename)

            if match:
                metadata = match.groupdict()
                zid = int(metadata['zebra'])
                n = int(metadata['n'])
                error_on_position = metadata['error_on_position']
                error_on_position = float(error_on_position) if error_on_position is not None else None

                # Automatically process any zebra ID found in the folder
                grouped_files[(zid, n, error_on_position)].append(file_path)
                discovered_zebras.add(zid)

        print(f"Discovered zebras in this experiment: {sorted(list(discovered_zebras))}")

        # Re-organize configurations by (n, error_on_position) so we can plot
        # multiple zebras together without mixing different position errors.
        scenarios = defaultdict(list)

        for (zid, n, error_on_position), files in grouped_files.items():
            dfs = []
            for f in files:
                dfs.append(read_estimation(f))

            # Mean across the index (time steps)
            # We wrap it in a try-except block just in case the concatenation is completely empty
            try:
                df_estimation_aggregated = pd.concat(dfs).groupby(level=0).mean()
            except ValueError:
                print(f"Warning: Could not aggregate estimations for Zebra {zid}. Moving to next.")
                continue

            # Load the real trajectory (handling the padding with zeros like zebra_035.csv)
            z_str = str(zid).zfill(3)
            real_path = f'{real_base_path}/zebra_{z_str}.csv'

            try:
                df_real = read_real_trajectory(real_path)
            except FileNotFoundError:
                print(f"Warning: Real trajectory not found at '{real_path}'. Skipping Zebra {zid}.")
                continue

            entity = {
                'name': f'Zebra {zid}',
                'real': df_real,
                'estimation': df_estimation_aggregated
            }

            scenarios[(n, error_on_position)].append((zid, entity))

        # Generate configurations for isolated and combined plots
        plot_configs = {}

        for (n, error_on_position), entities_list in scenarios.items():

            combined_entities = []

            # Determine the suffix to use based on experiment type, applying LaTeX styling for N
            exp_suffix = "Leader Based" if is_lb_experiment else f"Neighbor Based $|N|={n}$"
            if error_on_position is not None:
                exp_suffix = f"{exp_suffix} (errorOnPosition={error_on_position:g})"

            for zid, entity in entities_list:
                # 1. Configuration for isolated plot
                title = f'Zebra {zid} {exp_suffix}'
                plot_configs[title] = [entity]
                combined_entities.append(entity)

            # 2. Configuration for combined plot
            if len(combined_entities) > 1:
                combined_title = f'Combined Trajectories {exp_suffix}'
                plot_configs[combined_title] = combined_entities

        if not plot_configs:
            print(f"No matchable data/trajectories found in '{exp_data_path}'.")
        else:
            # Generate all charts for the current experiment
            generate_charts(plot_configs, exp_charts_path)
            print(f"Charts for '{current_experiment}' successfully generated in '{exp_charts_path}'.")

    print("\nAll experiments processed.")