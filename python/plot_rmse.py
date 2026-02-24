import re
import matplotlib
import numpy as np
import pandas as pd
import seaborn as sns
from pathlib import Path
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

def compute_rmse(df_true, dfs, max_time=3000):
        true_x = df_true['PositionX'].values[:max_time]
        true_y = df_true['PositionY'].values[:max_time]
        all_errors = []

        for df in dfs:
            est_x = df['estimatedX'].values[:max_time]
            est_y = df['estimatedY'].values[:max_time]
            dist = np.sqrt((true_x - est_x)**2 + (true_y - est_y)**2)
            all_errors.append(dist)
        return np.mean(np.array(all_errors), axis=0)

def compute_mean_stdev_rmses(rmses):
    rmse_matrix = np.array(list(rmses.values()))
    means = np.mean(rmse_matrix, axis=0)
    stddev = np.std(rmse_matrix, axis=0, ddof=1)
    means = means.tolist()
    stddev = stddev.tolist()
    return means, stddev

def beutify_experiment_name(name):
    return name

def plot_rmse(data, charts_path):
    #sns.set_theme(style="whitegrid")

    n_exp = len(data)
    palette = sns.color_palette("viridis", n_exp)
    plt.figure(figsize=(10, 6))

    for i, (name, (means, stds)) in enumerate(data.items()):
        means = np.array(means)
        stds = np.array(stds)
        t = np.arange(len(means))
        color = palette[i]

        plt.plot(t, means, label=beutify_experiment_name(name), color=color, lw=2)

        plt.fill_between(
            t,
            means - stds,
            means + stds,
            color=color,
            alpha=0.6,
            edgecolor=None
        )

    plt.xlabel("Time Step", fontsize=30)
    plt.ylabel("RMSE", fontsize=30)
    plt.xticks(fontsize=18)
    plt.yticks(fontsize=18)
    plt.grid(True, linestyle='--', alpha=0.6)
    plt.yscale('log')
    plt.legend(title="|N|", fontsize=18, title_fontsize=22, loc='center right')
    plt.tight_layout()
    plt.savefig(f'{charts_path}/rmse.pdf')
    plt.close()

if __name__ == '__main__':


    experiments = [0, 1, 4, 7]

    max_seed = 30

    data = {}

    charts_path = f'charts/'
    Path(charts_path).mkdir(parents=True, exist_ok=True)
    num_sensors = 25

    for experiment in experiments:
        data_path = f'data'

        rmses_by_seed = {}

        for seed in range(max_seed):
            df_true = read_alchemist_csv(f'{data_path}/real-trajectory_numberOfNeighbors-{experiment}_seed-{seed}.0.csv')
            df_true = df_true.dropna()

            dfs = []
            for i in range(num_sensors):
                df_estimation = pd.read_csv(f'{data_path}/estimations_node-{i}_n-{experiment}_seed-{seed}.0.csv')
                dfs.append(df_estimation)

            rmses = compute_rmse(df_true, dfs)
            rmses_by_seed[seed] = rmses

        m, s = compute_mean_stdev_rmses(rmses_by_seed)
        data[experiment] = (m,s)

    plot_rmse(data, charts_path)





