FROM python:3.14.0
RUN mkdir /experiment
WORKDIR /experiment
COPY .python-version ./
COPY python/plotter ./
COPY src/main/resources ./src/main/resources
COPY requirements.txt ./
RUN pip install --upgrade pip
RUN pip install -r requirements.txt
CMD sh -c "python plot-aggregated-h-comparison.py && python plot-rmse.py && python plot_moving_sensors_error_rmse.py && python plot_sensors_zebras_movement.py && python plot_leader_kill.py"
