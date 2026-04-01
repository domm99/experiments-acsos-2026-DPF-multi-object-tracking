import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path

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

    # Iterate over each configuration to create completely separate figures
    for title, entities in plot_configs.items():
        fig, ax = plt.subplots(figsize=(10, 10), constrained_layout=True)

        # 1. Calculate global MIN and MAX time steps across all entities in this plot
        # This ensures the colormap applies colors synchronously to all zebras
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

        # 2. Plot each zebra dynamically
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
            # We force vmin and vmax so that time steps perfectly match the color mapping across zebras
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

            # Mark start and end points (only labeled once to avoid legend clutter)
            start_label = 'Start' if idx == 0 else None
            end_label = 'End' if idx == 0 else None

            ax.scatter(df_true['x'].iloc[0], df_true['y'].iloc[0],
                       color='green', s=150, zorder=5, edgecolors='black', label=start_label)

            ax.scatter(df_true['x'].iloc[-1], df_true['y'].iloc[-1],
                       color='red', s=150, zorder=5, edgecolors='black', label=end_label)

            # Update overall plot bounds dynamically
            min_x = min(min_x, df_true['x'].min(), df_est['estimatedX'].min())
            max_x = max(max_x, df_true['x'].max(), df_est['estimatedX'].max())
            min_y = min(min_y, df_true['y'].min(), df_est['estimatedY'].min())
            max_y = max(max_y, df_true['y'].max(), df_est['estimatedY'].max())

        # Set plot cosmetics
        ax.set_title(title, fontsize=35)
        ax.set_xlabel('X (m)', fontsize=25)
        ax.set_ylabel('Y (m)', fontsize=25)

        # Apply a 10% padding to bounds (fallback to 10 if bounds are 0)
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
        # Place legend below the plot to support 10+ items comfortably
        leg = ax.legend(by_label.values(), by_label.keys(), loc='upper center', bbox_to_anchor=(0.5, -0.15),
                        ncol=4, fontsize=15)

        # Resize legend markers
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
        safe_filename = title.replace(" ", "_").lower()
        plt.savefig(f'{charts_path}/{safe_filename}.pdf', bbox_inches='tight')
        plt.savefig(f'{charts_path}/{safe_filename}.png', bbox_inches='tight', dpi=300)
        plt.close()

if __name__ == '__main__':

    # ---------------------------------------------------------
    # QUICK CONFIGURATION AREA
    # ---------------------------------------------------------
    seed = 42
    node = 10
    n = 0
    flight_number = 1

    # Just add or remove Zebra IDs here. The script handles the rest!
    zebra_ids = [35, 38]
    # ---------------------------------------------------------

    charts_path = 'charts'
    Path(charts_path).mkdir(parents=True, exist_ok=True)

    real_base_path = f'src/main/resources/zebras-trajectories/flights/flight_{flight_number}_zebras'
    est_base_path = 'data'

    loaded_zebras = []
    plot_configs = {}

    # Dynamically load data for all specified zebras
    for zid in zebra_ids:
        # zfill(3) adds leading zeros to match the "zebra_035.csv" naming convention
        z_str = str(zid).zfill(3)

        entity = {
            'name': f'Zebra {zid}',
            'real': read_real_trajectory(f'{real_base_path}/zebra_{z_str}.csv'),
            'estimation': read_estimation(f'{est_base_path}/estimations_zebra{zid}_node-{node}_n-{n}_seed-{seed}.0.csv')
        }
        loaded_zebras.append(entity)

        # Add single-zebra configuration to generate isolated plots
        plot_configs[f'Zebra {zid}'] = [entity]

    # Add combined configuration to generate the global plot
    plot_configs['Combined Trajectories'] = loaded_zebras

    # Generate everything
    generate_charts(plot_configs, charts_path)