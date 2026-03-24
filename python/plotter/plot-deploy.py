import re
import numpy as np
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.colors import Normalize

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

def plot_deploy(df, time, RMSE_MIN, RMSE_MAX):

    sns.set_theme(style="whitegrid")
    plt.figure(figsize=(12, 8))

    plot = sns.scatterplot(
        data=df,
        x='X',
        y='Y',
        size='NumberOfNeighbors',
        hue='RMSE',
        palette='flare',
        hue_norm=Normalize(vmin=RMSE_MIN, vmax=RMSE_MAX),
        sizes=(60, 600),
        alpha=0.8,
        edgecolor='black',
        #legend=False
    )

    # for i in range(df.shape[0]):
    #     plt.text(
    #         x=df.X[i] + 0.3,
    #         y=df.Y[i] + 0.3,
    #         s=int(df.id[i]),
    #         fontsize=9,
    #         #color='darkred',
    #         fontweight='semibold',
    #     )


    norm = Normalize(vmin=RMSE_MIN, vmax=RMSE_MAX)
    sm = plt.cm.ScalarMappable(cmap="flare", norm=norm)
    sm.set_array([])

    plot.get_legend().remove()

    plt.colorbar(sm, ax=plot, label='RMSE')

    handles, labels = plot.get_legend_handles_labels()
    #plot.legend(handles[-(len(labels)//2):], labels[-(len(labels)//2):],
    #                  bbox_to_anchor=(1.2, 1), loc='upper left', title='Error')

    plt.title(f'Error per sensor - Time {time}', fontsize=15)
    plt.xlabel('X(m)')
    plt.ylabel('Y(m)')

    plt.tight_layout()
    plt.savefig(f'charts/deploy-time_{time}.pdf')
    plt.close()

def find_min_max(dfs):
    global_min = min([df['RMSE'].min() for df in dfs])
    global_max = max([df['RMSE'].max() for df in dfs])
    return global_min, global_max

if __name__ == '__main__':

    df_deploy = pd.read_csv('data/sensors-positions.csv')

    df_true_trajectory = read_alchemist_csv('data/track-movement-neighboring-aggregation/track-movement-neighboring-aggregation_seed-42.0.csv')

    sensors = 9

    dfs_estimations = {}

    for i in range(sensors):
        data = pd.read_csv(f'data/estimations_node-{i}.csv')
        dfs_estimations[i] = data

    max_time = 2000
    plot_each = 50

    times_to_plot = range(0, max_time, plot_each)

    dfs = []

    for idx in times_to_plot:
        true_traj = df_true_trajectory.iloc[idx]
        errors = []
        for i in range(sensors):
            estimation = dfs_estimations[i].iloc[idx]
            e =  np.sqrt((true_traj['PositionX'] - estimation['estimatedX'])**2 + (true_traj['PositionY'] - estimation['estimatedY'])**2)
            errors.append(e)

        df = df_deploy.copy()
        df['RMSE'] = errors
        dfs.append(df)

    RMSE_MIN, RMSE_MAX = find_min_max(dfs)

    for time, df in zip(times_to_plot, dfs):
        plot_deploy(df, time, RMSE_MIN, RMSE_MAX)

