**CSV files with more than 1000 samples**, grouped by flight.

| Set | Flight | No. zebras | Zebras over 1000 samples                                                                                                                                                                                                                                                                                                                                                                                                     |
|---|---:|----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `flights` | `flight_1` |         3 | `zebra_035` 1628, `zebra_037` 1575, `zebra_038` 1449                                                                                                                                                                                                                                                                                                                                                                         |
| `flights` | `flight_2` |         1 | `zebra_002` 5776                                                                                                                                                                                                                                                                                                                                                                                                             |
| `flights` | `flight_4` |         6 | `zebra_001` 1770, `zebra_002` 2873, `zebra_003` 1620, `zebra_004` 4301, `zebra_005` 5053, `zebra_006` 5774                                                                                                                                                                                                                                                                                                                   |
| `flights` | `flight_5` |        14 | `zebra_001` 1674, `zebra_002` 1081, `zebra_007` 1400, `zebra_010` 1037, `zebra_032` 1197, `zebra_033` 1197, `zebra_038` 13519, `zebra_045` 2074, `zebra_051` 1824, `zebra_052` 8483, `zebra_057` 6725, `zebra_058` 6699, `zebra_059` 1406, `zebra_060` 4325                                                                                                                                                                  |
| `flights` | `flight_8` |         4 | `zebra_011` 1537, `zebra_017` 1356, `zebra_018` 1356, `zebra_019` 1285                                                                                                                                                                                                                                                                                                                                                       |
| `flights` | `flight_11` |        23 | `zebra_001` 1360, `zebra_002` 1748, `zebra_003` 1749, `zebra_004` 1749, `zebra_005` 1360, `zebra_006` 1748, `zebra_007` 1750, `zebra_008` 1749, `zebra_009` 1750, `zebra_021` 3007, `zebra_029` 1468, `zebra_030` 3005, `zebra_037` 1699, `zebra_038` 3004, `zebra_039` 4440, `zebra_041` 1338, `zebra_042` 1538, `zebra_050` 2858, `zebra_054` 2468, `zebra_056` 2588, `zebra_057` 2856, `zebra_058` 2585, `zebra_059` 1821 |
| `flights` | `flight_12` |        20 | `zebra_002` 6080, `zebra_004` 5489, `zebra_006` 4360, `zebra_007` 14787, `zebra_008` 18426, `zebra_011` 2346, `zebra_012` 2766, `zebra_013` 20917, `zebra_014` 10101, `zebra_015` 19843, `zebra_016` 15005, `zebra_017` 22245, `zebra_018` 11016, `zebra_019` 9919, `zebra_022` 7682, `zebra_023` 5768, `zebra_027` 1906, `zebra_028` 2603, `zebra_032` 1367, `zebra_034` 1329                                               |
| `flights` | `flight_13` |         7 | `zebra_002` 3186, `zebra_003` 1560, `zebra_005` 1548, `zebra_006` 2002, `zebra_007` 1785, `zebra_009` 1644, `zebra_015` 2112                                                                                                                                                                                                                                                                                                 |
| `flights` | `flight_14` |        17 | `zebra_001` 1786, `zebra_002` 5497, `zebra_003` 2370, `zebra_005` 1412, `zebra_007` 20943, `zebra_008` 4151, `zebra_009` 19660, `zebra_010` 9266, `zebra_011` 19318, `zebra_012` 1694, `zebra_014` 2286, `zebra_015` 14364, `zebra_018` 2833, `zebra_023` 10393, `zebra_024` 10055, `zebra_028` 4789, `zebra_030` 1470                                                                                                       |

------

| Flight | Zebra | Length | Samples | Average speed | Stationary steps | Speed while moving |
|---|---|---:|---:|---:|---:|---:|
| `flight_1` | `zebra_035` | 197.033 | 1628 | 0.1211 | 25.6% | 0.1628 |

Most similar trajectories to `zebra_035`, among the long ones:

| Rank | Flight | Zebra | Length | Samples | Average speed | Stationary steps | Notes |
|---:|---|---|---:|---:|---:|---:|---|
| 1 | `flight_4` | `zebra_001` | 177.856 | 1770 | 0.1005 | 31.0% | very good |
| 2 | `flight_1` | `zebra_037` | 163.309 | 1575 | 0.1038 | 25.3% | very similar in terms of stationary behavior |
| 3 | `flight_4` | `zebra_003` | 158.029 | 1620 | 0.0976 | 32.7% | good |
| 4 | `flight_5` | `zebra_032` | 164.829 | 1197 | 0.1378 | 24.2% | faster than zebra 035, excellent candidate |
| 5 | `flight_5` | `zebra_001` | 154.494 | 1674 | 0.0923 | 40.2% | still acceptable, but more stationary |
| 6 | `flight_4` | `zebra_002` | 237.879 | 2873 | 0.0828 | 41.0% | longer, less dynamic |
| 7 | `flight_4` | `zebra_004` | 353.831 | 4301 | 0.0823 | 42.4% | long, but already more pause-heavy |

Comparison with the trajectory that looked stationary:

| Flight | Zebra | Length | Samples | Average speed | Stationary steps |
|---|---|---:|---:|---:|---:|
| `flight_2` | `zebra_002` | 353.641 | 5776 | 0.0612 | 54.3% |

Recommended shortlist for trajectories similar to zebra 035, but still long and dynamic:

```text
flight_5/zebra_032
flight_1/zebra_037
flight_4/zebra_001
flight_4/zebra_003
```

If more length is preferred, even at the cost of more pauses:

```text
flight_4/zebra_002
flight_4/zebra_004
```

Avoid `flight_2/zebra_002` if the goal is to use lively trajectories:
it is long, but it remains stationary for more than half of the steps.
