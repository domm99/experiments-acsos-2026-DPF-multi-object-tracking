import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt


df = pd.read_csv('data/metrics.csv')


plt.figure(figsize=(10, 6))

sns.lineplot(
    data=df,
    x='stddev',
    y='armse',
    hue='experiment',
    style='experiment',
    markers=False,  #
    dashes=False,
    alpha=0.6,
    legend=False
)

plot = sns.scatterplot(
    data=df,
    x='stddev',
    y='armse',
    hue='experiment',
    s=100,
    style='experiment'
)

plt.xlabel('Measurement noise')
plt.ylabel('ARMSE')
plt.grid(True, linestyle='--', alpha=0.6)
plt.legend(title='Experiment')

plt.savefig('charts/armse.pdf')
plt.close()
