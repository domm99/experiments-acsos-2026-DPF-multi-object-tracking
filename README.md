# TODO TITLE

This artifact is associated with the regular paper article submitted in the Main Track of the ACSOS 2026 conference.

### Authors
Anonimized for double-blind review.

[//]: # ()
[//]: # (| **Angela Cortecchia** &#40;*&#41;  | **Davide Domini** &#40;*&#41;  | **Giovanni Ciatto**&#40;*&#41;   | **Roberto Casadei**&#40;*&#41; | **Mirko Viroli** &#40;*&#41;  |)

[//]: # (|:--------------------------:|:----------------------:|--------------------------|------------------------|:---------------------:|)

[//]: # (| angela.cortecchia@unibo.it | davide.domini@unibo.it | giovanni.ciatto@unibo.it | roby.casadei@unibo.it  | mirko.viroli@unibo.it |)

[//]: # ()
[//]: # (&#40;*&#41;)

[//]: # (*Department of Computer Science and Engineering )

[//]: # (Alma Mater Studiorum --- Università di Bologna - Cesena, Italy*)

### Table of Contents
- [About](#about)
    * [Experiments](#experiments)
- [Getting Started](#getting-started)
    - [Requirements](#requirements)
    - [Limitations](#limitations)
    - [Understanding the experiments](#understanding-the-experiments)
    - [Walk-through the experiments](#walk-through-the-experiments)
    - [Reproduce the entire experiment](#reproduce-the-entire-experiment)
        * [Simulation Graphical Interface](#simulation-graphical-interface)
        * [Extremely quick-start of a basic experiment -- `(ba|z|fi)?sh` users only](#extremely-quick-start-of-a-basic-experiment----bazfish-users-only)
        * [Reproduce the experiments through Gradle](#reproduce-the-experiments-through-gradle)
        * [Changing experiment's parameters](#changing-experiments-parameters)
        * [Project structure](#project-structure)
        * [Simulation entrypoint](#simulation-entrypoint)
    - [Reproduce the experiment results](#reproduce-the-experiment-results)
        * [Reproduce the experiments with containers (recommended)](#reproduce-the-experiments-with-containers-recommended)
        * [Reproduce natively](#reproduce-natively)
        * [Generate the charts](#generate-the-charts)

## About

TODO ABSTRACT

### Experiments

The repository currently contains three main experiment configurations and two smaller support scenarios:

- `fixedSensorsNB`: fixed sensor grid, neighbor-based filtering, batch sweep on `numberOfNeighbors` in `[0, 1, 2, 4, 7]`.
- `fixedSensorsLB`: fixed sensor grid, leader-based filtering, one elected leader performs the aggregation/update step.
- `movingSensorsNB`: neighbor-based filtering with the sensor grid moving as a swarm around the zebra centroid.

## Getting Started

### Requirements

In order to successfully download and execute the graphical experiments, you will need:

- Internet connection;
- [Git](https://git-scm.com);
- Linux, macOS, or Windows;
- [Java](https://adoptium.net/) 17 or newer;
- about 4GB of free disk space for Gradle dependencies and generated outputs;
- GPU with basic OpenGL support for the Alchemist graphical interface;
- at least 4GB of RAM for interactive runs, and preferably more for batch executions.

To reproduce the result-processing pipeline natively, you will also need:

- Python `3.14.0` recommended, matching `.python-version`;
- `pip` to install the dependencies listed in `requirements.txt`.

To use the container-based workflow, you will additionally need:

- [Docker](https://www.docker.com/);
- Docker Compose.

The project uses the [Gradle](https://gradle.org) wrapper included in the repository,
so a separate Gradle installation is not required.
The main JVM dependencies are declared in `gradle/libs.versions.toml`,
and the simulation tasks are generated automatically from the YAML files in `src/main/yaml`.

### Limitations

- The experiments run in "batch mode" generate a lot of data,
  and the simulation may take a long time to finish (up to several hours) even with high-performance computers.
  We suggest running the experiments in "graphic mode" to have a better understanding of the simulation;
- On different monitor types with different resolutions, the graphical interface could appear a bit different;
- "batch mode" does not show any graphical interface;
- For GUI interpretation, please refer to the [Simulation Graphical Interface](#simulation-graphical-interface) section;
- Due to Alchemist's limitations, the graphical interface will not appear if run on a docker container.

### Understanding the experiments

Each main experiment combines the same building blocks:

- zebra trajectories are replayed by the custom `MoveNode` action from CSV files in `src/main/resources/zebras-trajectories/`;
- filter nodes run an aggregate program declared in the YAML file;
- the aggregate program uses the `ParticleFilter` implementation in `src/main/kotlin/it/unibo/filtering/ParticleFilter.kt`;
- exported estimates are written to `data/<experiment-name>/estimations_zebra<id>_node-<id>_n-<neighbors>_seed-<seed>.csv`.

The important difference between the main configurations is how information is combined:

- in `fixedSensorsNB`, each sensor keeps a fixed position and fuses its own information with a bounded number of nearby neighbors;
- in `fixedSensorsLB`, sensors stay fixed as well, but the update step is centralized on the elected leader of the current aggregate neighborhood;
- in `movingSensorsNB`, the sensing grid follows the centroid of the zebras, so the observation geometry changes over time.

### Walk-through the experiments

TODO

### Reproduce the entire experiment

**WARNING**: re-running the whole experiment may take a very long time on a normal computer.

#### Simulation Graphical Interface

The simulation environment and graphical interface are provided by
[Alchemist Simulator](https://alchemistsimulator.github.io/index.html).
To understand how to interact with the GUI,
please refer to the
[Alchemist Swing documentation](https://alchemistsimulator.github.io/reference/swing/index.html#shortcuts).

The JSON files under `effects/` define the visual overlays used by the supported graphic runs.
At the moment, effect files are available for:

- `fixedSensorsLB`
- `fixedSensorsNB`
- `movingSensorsNB`

#### Extremely quick-start of a basic experiment -- `(ba|z|fi)?sh` users only

- Requires a Unix terminal (`(ba|z|fi)?sh`)
- `curl` must be installed
- run:
```bash
# Fixed Sensors Leader Based experiment with default parameters and graphical interface
curl https://raw.githubusercontent.com/domm99/experiments-acsos-2026-DPF-multi-object-tracking/refs/heads/master/fixed-sensors-leader-based.sh | bash 
```
or
```bash
# Moving Sensors Neighbors Based experiment with default parameters and graphical interface
curl https://raw.githubusercontent.com/domm99/experiments-acsos-2026-DPF-multi-object-tracking/refs/heads/master/moving-sensors-neighbors-based.sh | bash 
```
- the repository is in your `Downloads` folder for further inspection.

#### Reproduce the experiments through Gradle

1. Install a Gradle-compatible version of Java.
   Use the [Gradle/Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html) to learn which is the compatible version range.
   The Version of Gradle used in this experiment can be found in the gradle-wrapper.properties file located in the gradle/wrapper folder.
2. Open a terminal
3. Clone this repository on your pc with `git clone https://github.com/domm99/experiments-acsos-2026-DPF-multi-object-tracking`.
4. Move into the root folder with `cd experiments-acsos-2026-DPF-multi-object-tracking`
5. Depending on the platform, run the following command:
    - Bash compatible (Linux, Mac OS X, Git Bash, Cygwin): ``` ./gradlew run<ExperimentName>Graphic ```
    - Windows native (cmd.exe, Powershell): ``` gradlew.bat run<ExperimentName>Graphic ```
6. Substitute `<ExperimentName>` with the name of the experiment (in PascalCase) specified in the YAML simulation file.
   Or execute ```./gradlew tasks``` to view the list of available tasks.

**NOTES:**
- Due to Alchemist's limitations, the graphical interface will not appear if run on a docker container.
- The tasks *in graphic mode* will run the experiments with the default parameters.
- Graphic tasks run with the default parameters defined in the YAML.

**Note** that before each experiment command, it can be optionally set the `MAX_SEED` environment variable to a specific value to run the experiment,
since that parameter is relevant only for batch experiments,
it is suggested to not specify it or set it to `0` for the graphical experiments.

Depending on the platform, there may be different ways to set the environment variable:
- If you're using Bash compatible (Linux, Mac OS X, Git Bash, Cygwin): ```MAX_SEED=0 ./gradlew run<ExperimentName>Graphic```
- If you're using Command Prompt (cmd.exe): ```set MAX_SEED=0 && gradlew.bat run<ExperimentName>Graphic```
- If you're using PowerShell: ```$env:MAX_SEED = 0; .\gradlew.bat run<ExperimentName>Graphic``` \
  For the sake of simplicity, we will show Bash compatible commands below.
  Moreover, due to Alchemist's limitations, the graphical interface will not appear if run on a docker container.

The corresponding YAML simulation files to the experiments cited above are the following,
- _fixedSensorsLB_
- _fixedSensorsNB_
- _movingSensorsNB_

**Notes:**
- due to Alchemist limitations, the graphical interface is intended for local/native execution rather than containerized runs.

#### Changing experiment's parameters

To change the parameters of the experiments, edit the YAML files in `src/main/yaml/`.

The parameters you will most likely tune are:

- `numberOfParticles`
- `maxInitialSpeed`
- `neighboringDistance`
- `numberOfNeighbors`
- `stepLength`
- `seed`

Each change in the parameters will result in a different setup and execution of the experiment.
The parameters provided in the YAML files are the ones used for the evaluation and the ones evaluated as "optimal."

For more information about the simulation DSL,
please refer to the
[Alchemist YAML documentation](https://alchemistsimulator.github.io/reference/yaml/index.html).

#### Project structure

The project is currently organized as follows:

```text
experiments-acsos-2026-DPF-multi-object-tracking/ 
├── python/                # Plotting utilities
├── docker/                 # Dockerfiles to build containers
├── effects/                # Json specification for Alchemist's GUI visualization
├── gradle/                 # Gradle wrapper files
├── src/
│   └── main/
│     ├──kotlin/it/unibo/     # Kotlin source code for the experiments
|     │   ├── alchemist       # Alchemist's model and global reactions
│     │   │   └── collektive/device # Collektive device integration for Alchemist
│     │   ├──collektive/
│     │   │   ├──alchemist/device/sensors/  # Sensors for the experiments, including random generator and time sensor
│     │   │   ├──model/            # Utilities   
│     │   │   ├──stdlib/           # Leader election strategies  
│     │   │   ├──experiments/      # Entrypoints for the experiments
│     │   └──filtering/            # Particle filter implementation
│     ├── resources/               # Stores the zebra trajectories used within the simulations;
│     └── yaml/                    # YAML files for the experiments specification
```

#### Simulation entrypoint

The main aggregate entrypoints used right now are:

- `it.unibo.collektive.AggregateInformationAgentKt.informationFilterEntrypoint`
- `it.unibo.collektive.AggregateInformationAgentLeaderBasedKt.informationFilterEntrypointLeaderBased`

TODO

### Reproduce the experiment results

**WARNING**: re-running the whole experiment may take a very long time on a normal computer.

For the current project status, result reproduction means:

- running the batch simulations so that CSV estimations are exported under `data/`;
- post-processing those CSV files with the Python scripts under `python/plotter/`;
- optionally adapting the plotting scripts to the exact set of experiments you want to compare.

#### Reproduce the experiments with containers (recommended)

1. Install [Docker](https://www.docker.com/products/docker-desktop) and [docker-compose](https://docs.docker.com/compose/install/);
2. Run `docker-compose up` in the root folder of the repository:
   this will build the Docker images and run the containers needed to run the experiments.
3. From the `docker-compose.yml` file, you can see that three separate containers will be created, one for each experiment, and the data will be collected in the `data` folder.
   Note that the `volumes` field has to be updated to match your local environment.
   You may need to adjust the `volumes` paths to match your machine.

#### Reproduce natively

1. Install a Gradle-compatible version of Java.
   Use the [Gradle/Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
   to learn which is the compatible version range.
   The Version of Gradle used in this experiment can be found in the `gradle-wrapper.properties` file
   located in the `gradle/wrapper` folder.
2. Install the version of Python indicated in `.python-version` (or use `pyenv`).
3. Launch either:
    - `./gradlew runAllBatch` on Linux, MacOS, or Windows if a bash-compatible shell is available;
    - `gradlew.bat runAllBatch` on Windows cmd or Powershell;
4. Once the experiment is finished, the results will be available in the `data` folder,
   **if data extraction is properly configured in the YAML files**.

#### Generate the charts

**WARNING**: depending on the amount of data collected, this process may take a long time.

1. Make sure you have Python 3.10 or higher installed.
2. The data folder structure should be the following:
    ```txt
    experiments-acsos-2026-DPF-multi-object-tracking/
    ├── data/
    │   ├── <experiment-name>/
    │   ├── <experiment-name2>/
    │   └── .../
    ```
3. Install the required Python packages by running:
    ```bash
    pip install --upgrade pip
    pip install -r requirements.txt
    ```
4. Run the script to process the data and generate the charts (this process may take some time):
    ```bash
    python plot.py TODO FIX THIS
    ```
5. The charts will be generated in the `charts` folder.
6. If you want to regenerate the charts, you can run the script again.
