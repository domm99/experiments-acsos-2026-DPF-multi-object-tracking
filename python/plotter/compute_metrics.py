import re
import glob
import numpy as np
import pandas as pd
import seaborn as sns
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

def compute_metrics(true_df, estimates, lost_thresh=4):

    true_pos = true_df.values
    T = len(true_pos)

    errors = {}
    for k, df_k in estimates.items():
        est_pos = df_k.values
        err = np.linalg.norm(est_pos - true_pos, axis=1)
        errors[k] = err

    squared_errors_stack = np.vstack([errors[k]**2 for k in errors])  # shape (K, T)
    RMSE_n = np.sqrt(np.mean(squared_errors_stack, axis=0))

    ARMSE = np.sqrt(np.mean(RMSE_n**2))

    ARMSE_k = {k: np.sqrt(np.mean(errors[k]**2)) for k in errors}

    delta_ARMSE = np.std(list(ARMSE_k.values()))

    lost_tracks = {k: (errors[k][-1] > lost_thresh) for k in errors}
    num_lost = sum(lost_tracks.values())
    K = len(estimates)
    PLT = 100.0 * num_lost / K

    return RMSE_n, ARMSE, delta_ARMSE, ARMSE_k, PLT, lost_tracks

def plot_rmse_over_time(RMSE_n):

    RMSE_n = np.asarray(RMSE_n)
    t = np.arange(len(RMSE_n))

    sns.set(style="whitegrid")

    plt.figure(figsize=(8, 4))
    sns.lineplot(x=t, y=RMSE_n)

    plt.xlabel("Time step")
    plt.ylabel("RMSE")

    plt.tight_layout()
    plt.savefig('charts/RMSE.pdf')
    plt.close()

if __name__ == '__main__':

    csv_file = 'data/track-movement-distributed/track-movement-distributed_numberOfParticles-250_maxInitialSpeed-2.0.csv'
    max_simulation_time = 3000
    Path('charts').mkdir(parents=True, exist_ok=True)

    lines = np.matrix(openCsv(csv_file))
    vars =  extractVariableNames(csv_file)
    vars = [v.split('[')[0] for v in vars]
    df = pd.DataFrame(data=lines, columns=vars)
    df_true_positions = df.dropna()
    df_true_positions = df_true_positions.drop(columns=["time"])
    df_true_positions = df_true_positions.head(max_simulation_time)

    centralized = False

    estimates = {}

    if centralized:
        df_estimation = pd.read_csv('data/estimations.csv')
        df_estimation = df_estimation.head(max_simulation_time)
        estimates['node_0'] = df_estimation
    else:
        files = glob.glob('data/estimations_node-*.csv')
        for index, file in enumerate(files):
            df_estimation = pd.read_csv(file)
            df_estimation = df_estimation.head(max_simulation_time)
            estimates[f'node_{index}'] = df_estimation

    RMSE_n, ARMSE, delta_ARMSE, ARMSE_k, PLT, lost_tracks = compute_metrics(df_true_positions, estimates, lost_thresh=5.0)


    print(f'ARMSE: {ARMSE}')
    print(f'delta_ARMSE: {delta_ARMSE}')
    print(f'PLT: {PLT}')
    print(f'lost_tracks: {lost_tracks}')

    plot_rmse_over_time(RMSE_n)
