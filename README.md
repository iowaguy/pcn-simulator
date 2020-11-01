# Running the Simulator
The simulator can be run in one of two ways. It can either be run as a
standalone JVM application, or as part of an experiment which will run many JVMs
(i.e. simulations). Before running in either mode, the simulator must be
compiled; this can be done by running `mvn clean install`. This simulator
requires Java 8, so you must set the JAVA_HOME environment variable appropriately.

## Standalone Mode
Standalone mode can be used to run one of the routing algorithms for a
particular scenario. It will run the simulator as a single Java appliation. The
simulator will take in a configuration file. For the most updated list of
available fields, see the file `RunConfig.java` in the `treeembedding` package.
The configuration file must be YAML formatted and must be named `runconfig.yml`.

Execute the following command to run the simulation:
```java
$JAVA_HOME/bin/java -cp target/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies.jar treeembedding.runners.Dynamic <DIR>
```
`<DIR>` is the directory where `runconfig.yml` is stored and is also where the
output of the simulation will be written.

## Experiment Mode
### Setup
To run many simulations as part of an experiment, the Python library
[ipyparallel](https://ipyparallel.readthedocs.io/en/latest/) is used. Many other
python modules are also used, and getting the versions correct is important. For
this, the recommended setup is to use to
[virtualenv](https://pypi.org/project/virtualenv/) and
[virtualenvwrapper](https://virtualenvwrapper.readthedocs.io/en/latest/). To
install these, follow the instructions on their respective websites.

To create the enviroment we need, create a virtual env name pcnenv with Python
3.6 by running:
```bash
mkvirtualenv pcn -p python3 -r requirements.txt
```
This will install the basics and put you in the env. In the future, when you
will only need to run: `workon pcnenv` to enter the virtual environment where
all the dependencies are installed.

### Running ipyparallel
We require two `IPyParallel` components: controllers and engines. The controller
is the cluster leader, it's where jobs are submitted and where the load
balancing is done. To start the controller, enter the following command on your
leader node (where you plan to start the simulations from):
```bash
./sim controller
```

The number of `IPyParallel` engines dictates how many concurrent JVMs (read:
individual simulations) will be run at once. For experiments with many
concurrent transaction, a single node may not be able to run more than one
engine due to resource constraints.

To start engines, on each node run:
```bash
./sim engines N
```
Where N is the number of engines to start. If you see something like `./sim:
line 47: ipcluster: command not found`, there is probably an issue with your
environment, you might not be in your virtualenv. Otherwise, if no errors are
received, wait until the following message appears (should take less than a
minute), then hit Ctrl-C.
```
2020-01-22 10:57:29.418 [IPClusterStart] Engines appear to have started successfully
```

Later, when the experiments are done running, stop the engines with `./sim
stop`; this must be run on all nodes in the cluster. You can see the current
status of your ipcluster by running `./sim status`.

### Running An Experiment
An experiment takes in a generator script. This script will generate the
configurations for each individual simulation; see `experiments.py`. Note that
the `generate_configs()` function creates a list of lists. This is done to
enforce a partial ordering on the simulations. This is needed because in the
Dynamic simulator, a single simulation may consist of several steps (often 9 or
10), and for a given setup, these steps need to happen in order.

The list of available experiments can be viewed by running `./sim experiment
list`.

To start an experiment, run `./sim experiment <EXPERIMENT_NAME>`, where
`<EXPERIMENT_NAME>` is one of the provided experiments from the list command. New
experiments can be created by defining them in `experiments.py`.

There is not much in the way of tracking progess, but it can be helpful to keep
an eye on `htop`. Note that every experiment will end up in a different
subdirectory. However, if the same experiment is attempted to run twice, it will
fail the second time unless the `force_overwrite` option is set to `true`.
