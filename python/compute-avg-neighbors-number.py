import pandas as pd

if __name__ == "__main__":
    df = pd.read_csv("data-2n/sensors-positions_seed-42.0.csv")
    print(df['NumberOfNeighbors'].mean())