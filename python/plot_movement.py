import re
import numpy as np
import pandas as pd
from pathlib import Path
import matplotlib.pyplot as plt
import matplotlib

def openCsv(path):
    regex = re.compile('\d')
    with open(path, 'r') as file:
        lines = filter(lambda x: regex.match(x[0]), file.readlines())
        return [[float(x) for x in line.split()] for line in lines]

def extractVariableNames(filename):
    with open(filename, 'r') as file:
        dataBegin = re.compile('\d')
        lastHeaderLine = ''
        for line in file:
            if dataBegin.match(line[0]):
                break
            else:
                lastHeaderLine = line
        if lastHeaderLine:
            regex = re.compile(' (?P<varName>\S+)')
            return regex.findall(lastHeaderLine)
        return []

def read_alchemist_csv(path):
    lines = np.matrix(openCsv(path))
    vars =  extractVariableNames(path)
    vars = [v.split('[')[0] for v in vars]
    df = pd.DataFrame(data=lines, columns=vars)
    df = df.dropna()
    return df

def beautify_experiment_name(name):
    return name

def generate_charts(data_dict, charts_path, filename):

    num_experiments = len(data_dict)
    side_length = 100

    fig, axes = plt.subplots(1, num_experiments, figsize=(8 * num_experiments, 8),
                             constrained_layout=True)

    if num_experiments == 1:
        axes = [axes]

    last_scatter = None

    for ax, (name, (df_true, df_estimation, sensors_positions)) in zip(axes, data_dict.items()):

        ax.plot(df_true['PositionX'], df_true['PositionY'],
                label='Real Trajectory', color='black', linestyle='--', linewidth=2, alpha=0.5)

        last_scatter = ax.scatter(
            df_estimation['estimatedX'],
            df_estimation['estimatedY'],
            c=df_estimation.index,
            cmap='viridis',
            s=20,
            alpha=0.8,
            zorder=4,
            label='Estimated'
        )

        ax.scatter(df_true['PositionX'].iloc[0], df_true['PositionY'].iloc[0],
                   color='green', s=150, zorder=5, edgecolors='black', label='Start')
        ax.scatter(df_true['PositionX'].iloc[-1], df_true['PositionY'].iloc[-1],
                   color='red', s=150, zorder=5, edgecolors='black', label='End')
#  ax.scatter(
#             sensors_positions['X'],
#             sensors_positions['Y'],
#             s=150,
#             zorder=3,
#             color='black',
#             edgecolors='black',
#             label='Sensors'
#         )


        ax.set_title(f'|N| = {name}', fontsize=32, pad=20)
        ax.set_xlim(-10, side_length)
        ax.set_ylim(-10, side_length)
        ax.set_xlabel('X (m)', fontsize=27)
        if ax == axes[0]:
            ax.set_ylabel('Y (m)', fontsize=27)

        ax.grid(True, linestyle='--', alpha=0.6)
        ax.set_aspect('equal', adjustable='box')
        ax.tick_params(labelsize=22)

    handles, labels = axes[0].get_legend_handles_labels()
    leg = fig.legend(handles, labels, loc='upper center', bbox_to_anchor=(0.5, 1.1),
               ncol=5, fontsize=25)

    for handle in leg.legend_handles:
        if hasattr(handle, 'set_sizes'):
            handle.set_sizes([150])
        elif hasattr(handle, 'set_linewidth'):
            handle.set_linewidth(3)

    if last_scatter:
        cbar = fig.colorbar(last_scatter, ax=axes, orientation='vertical', fraction=0.02, pad=0.04)
        cbar.set_label('Time Steps', fontsize=25)
        cbar.ax.tick_params(labelsize=20)
        vmin = df_estimation.index.min()
        vmax = df_estimation.index.max()
        cbar.set_ticks([vmin, vmax])
        cbar.set_ticklabels(['0', '3000'])

    plt.savefig(f'{charts_path}/{filename}.pdf', bbox_inches='tight')
    plt.close()

if __name__ == '__main__':

    experiments = [0, 1, 4, 7]

    #seed_to_plot = 32

    charts_path = f'charts/'
    Path(charts_path).mkdir(parents=True, exist_ok=True)
    data_path = f'data'

    for seed_to_plot in range(100):
        data = {}

    #     df_true = read_alchemist_csv(f'{data_path}/real-trajectory_seed-{seed_to_plot}.0.csv')
    #
    #     df_first_leader  = pd.read_csv(f'{data_path}/estimations_node-12_n-0_seed-{seed_to_plot}.0.csv')
    #     df_second_leader  = pd.read_csv(f'{data_path}/estimations_node-11_n-0_seed-{seed_to_plot}.0.csv')
    #     df_final = pd.concat([df_first_leader, df_second_leader], ignore_index=True)
    #     print(df_first_leader.shape)
    #     print(df_second_leader.shape)
    #     print(df_final.shape)
    #     sensors_positions = pd.read_csv(f'{data_path}/sensors-positions_n-0_seed-{seed_to_plot}.0.csv')
    #     data[0] = (df_true, df_final, sensors_positions)
    #     #data[1] = (df_true, df_second_leader, sensors_positions)
    #     generate_charts(data, charts_path, 'trajectories')

        for experiment in experiments:

            df_true = read_alchemist_csv(f'{data_path}/real-trajectory_numberOfNeighbors-{experiment}_seed-{seed_to_plot}.0.csv')
            #df_true = read_alchemist_csv(f'{data_path}/real-trajectory_seed-{seed_to_plot}.0.csv')

            dfs = []

            for i in range(25):
                df_estimation = pd.read_csv(f'{data_path}/estimations_node-{i}_n-{experiment}_seed-{seed_to_plot}.0.csv')
                dfs.append(df_estimation)

            df_estimation_aggregated = pd.concat(dfs).groupby(level=0).mean()

            sensors_positions = pd.read_csv(f'{data_path}/sensors-positions_n-{experiment}_seed-{seed_to_plot}.0.csv')

            data[experiment] = (df_true, df_estimation_aggregated, sensors_positions)

        generate_charts(data, charts_path, f'trajectories_seed-{seed_to_plot}')

