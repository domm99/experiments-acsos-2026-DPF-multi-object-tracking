import re
import numpy as np
import pandas as pd
from pathlib import Path
import matplotlib.pyplot as plt


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


def plot_particle_estimates(df, df_real, step=100, output_dir=None):

    num_particles = 250
    indices_to_plot = range(0, df_real.shape[0], step)
    for row_idx in indices_to_plot:
        row = df.iloc[row_idx]
        row_real = df_real.iloc[row_idx]

        plt.figure(figsize=(10, 8))

        xs = []
        ys = []
        ws = []

        for i in range(num_particles):
            x = row[f'p_{i}-X']
            y = row[f'p_{i}-Y']
            w = row[f'p_{i}-W']

            xs.append(x)
            ys.append(y)
            ws.append(w)

        sizes = [max(0.1, weight * 100000) for weight in ws]



        plt.scatter(xs, ys, s=sizes, alpha=0.6, edgecolors='none', color='blue', zorder=1)
        plt.scatter(row_real['PositionX'], row_real['PositionY'], s=800, edgecolors='none', color='red', zorder=2)

        plt.title(f"t={row_idx}", fontsize=40)
        plt.xlabel("X (m)", fontsize=35)
        plt.ylabel("Y (m)", fontsize=35)
        plt.xticks(fontsize=25)
        plt.yticks(fontsize=25)
        plt.grid(True, linestyle='--', alpha=0.5)

        plt.ylim(0, 100)
        plt.xlim(0, 100)

        filename = f"{output_dir}step_{row_idx}.pdf"
        plt.savefig(filename)
        plt.close()

if __name__ == '__main__':
    charts_path = 'charts/allparticles/'
    Path(charts_path).mkdir(parents=True, exist_ok=True)


    experiments = [('grid1x1', 1), ('grid2x1', 2), ('grid3x3', 9), ('grid5x5', 25)]

    seed = 42

    for experiment, nodes in experiments:

        data_path = f'data-{experiment}-graphic'

        df_real = read_alchemist_csv(f'{data_path}/track-movement-neighboring-aggregation_seed-{seed}.0.csv')
        for i in range(nodes):
            path = f'{charts_path}/{experiment}/node-{i}/'
            Path(path).mkdir(parents=True, exist_ok=True)

            df_particles = pd.read_csv(f'{data_path}/particles_node-{i}_seed-{seed}.0.csv')
            plot_particle_estimates(df_particles, df_real, 100, path)