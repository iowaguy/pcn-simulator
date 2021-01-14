
# Running the Simulator
The simulator can be run in one of two ways. It can either be run as a
standalone JVM application, or as part of an experiment which will run many JVMs
(i.e. simulations). Before running in either mode, the simulator must be
compiled; this can be done by running `mvn clean install -DskipTests`. This
simulator requires Java 8, so you must set the JAVA_HOME environment variable
appropriately.

## Dependencies
- Java (Java 8 or higher)
- Maven (on most OSes can be installed on command line)

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

## Simulator Parameters
The parameters that go in `runconfig.yml` are as follows:
- `base`: This is the path to the dataset directory.


| Parameter                     | Function                                                                                                                                                                                                                                                                                                  |
|- |- |
| base                          | This is the path to the dataset directory.                                                                                                                                                                                                                                                                |
| topology                      | The name of the topology file. Usually `topology.graph`.                                                                                                                                                                                                                                                  |
| transaction_set               | The name of the transaction set file. Usually `transactions.txt`                                                                                                                                                                                                                                          |
| simulation_type               | Recommended setting is `dynamic`. `static` has not been tested in a while.                                                                                                                                                                                                                                |
| force_overwrite               | boolean. Should prior results be overwritten?                                                                                                                                                                                                                                                             |
| routing_algorithm             | One of: `speedymurmurs` or `maxflow_collateralize`                                                                                                                                                                                                                                                        |
| attempts                      | Retries for failed transactions.                                                                                                                                                                                                                                                                          |
| trees                         | For `speedymurmurs`, the number of spanning trees to use.                                                                                                                                                                                                                                                 |
| iterations                    | The number of times to run the experiment (only 1 is supported)                                                                                                                                                                                                                                           |
| step                          | If the transaction set is split into distinct pieces, this indicates which piece to start from (0-indexed).  Usually, it's just 0                                                                                                                                                                         |
| concurrent_transactions       | boolean. Should there be multiple concurrent transactions?                                                                                                                                                                                                                                                |
| concurrent_transactions_count | integer. The number of concurrent transactions.                                                                                                                                                                                                                                                           |
| network_latency_ms            | integer. The simulated network latency, in milliseconds.                                                                                                                                                                                                                                                  |
| new_links_path                | string. The name of the file where new links (added dynamically) are defined.                                                                                                                                                                                                                             |
| log_level                     | One of: `error`, `warn`, `info`, `debug`, `trace`                                                                                                                                                                                                                                                         |
| epoch_length                  | How many transactions to group together. My preference is just 1, and then do the grouping in post-processing.                                                                                                                                                                                            |
| arrival_delay_ms              | How long to wait between transactions.                                                                                                                                                                                                                                                                    |
| attack_properties             | The key whose value is a set of YAML key/value pairs listed below                                                                                                                                                                                                                                         |
| attackers                     | integer. The number of attackers.                                                                                                                                                                                                                                                                         |
| attacker_selection            | One of: `random`, `selected`, or `none`. `random` selects attackers from throughout the topology randomly and without replacement. `selected` specifically chooses the attackers provided in `selected_byzantine_nodes`. `none` means no attackers are selected.                                          |
| attack_type                   | One of: `drop_all`, `griefing`, or `griefing_success`. `drop_all` will make a transaction immediately fail upon coming accross a byzantine node. `griefing_success` will delay the transaction for a time, but eventually let it continue. `griefing` will delay a transaction and then cause it to fail. |
| receiver_delay_ms             | integer. How long should the griefing delay be.                                                                                                                                                                                                                                                           |
| receiver_delay_variability    | Randomize delays by providing a margin (in milliseconds) from `receiver_delay_ms` by which the delay can deviate.                                                                                                                                                                                         |
| selected_byzantine_nodes      | A list of the node IDs of byzantine nodes.                                                                                                                                                                                                                                                                |


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
will only need to run: `workon pcn` to enter the virtual environment where
all the dependencies are installed.

The `JAVA_HOME` environment variable must be set in all shells. To do this, find
your `JAVA_HOME` and put the following in your `.bash_profile`.

``` bash
export JAVA_HOME=<your java_home path>
```

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

### Running An Experiment
An experiment takes in a python dict as its configuration. This configuration
will be used to generate configurations for each individual simulation. Many
simulations already exist in `experiments.py`. Note that the
`generate_configs()` function creates a list of lists. This is done to enforce a
partial ordering on the simulations. This is needed because in the Dynamic
simulator, a single simulation may consist of several steps (often 9 or 10), and
for a given setup, these steps need to happen in order.

The list of available experiments can be viewed by running `./sim experiment
list`.

To start an experiment, run `./sim experiment <EXPERIMENT_NAME>`, where
`<EXPERIMENT_NAME>` is one of the provided experiments from the `list` command.
New experiments can be created by defining them in `experiments.py`.

There is not much in the way of tracking progess, but it can be helpful to keep
an eye on `htop`. Note that every experiment will end up in a different
subdirectory. However, if the same experiment is attempted to run twice, it will
fail the second time unless the `force_overwrite` option is set to `true`.

The experiments used in [Exploiting
Centrality](https://arxiv.org/abs/2007.09047) are 22, 23, 30, 51, and 57.
However, several other experiments are provided.

### Defining An Experiment
An experiment is defined by creating a dict entry in the `experiments` dict in
`experiments.py`. The key is the experiment name that will be used to run the
experiment. The key/value pairs in the dictionary are defined in the following
table. Note that where lists are accepted, all permutations will be run. This
means that creating a list of *N* entries will increase the number of
simulations by a factor of *N* (unless otherwise specified).

| Key                           | Value                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| -                             | -                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| notes                         | This is a comment field that has no bearing on the simulation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| num_steps                     | How many steps are in each simulation, most datasets should just use `1`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| data_set_list                 | list. The full name of the dataset to be used. Only one at a time is supported right now.                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| concurrent_transactions_count | list. The number of concurrent transactions.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| routing_algorithms            | list. One of `common.speedymurmurs` or `common.maxflow`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| epoch_lengths_list            | list. Length of this list must correspond with the length of `data_set_list`.                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| network_latency_ms            | See simulator configs.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| attack_type                   | list. Of: `griefing_success`, `griefing`, or `drop_all`. See simulator configs for details.                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| receiver_delay_variability    | See simulator configs.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| receiver_delay_ms             | list. See simulator configs.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| attacker_selection            | One of: `selected` or `random`. See `attacker_selection` in simulator configs.                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| selected_byzantine_nodes      | list of tuples. First item in tuple is a method by which attackers can be selected, one of: `baseline`, `by_number_of_transactions`, `by_tree_depth`. `baseline` is just a placeholder that must be used for the 0 attacker case. `by_number_of_transactions` calculates how many transactions will pass through each node, and selects the highest ranking of those. `by_tree_depth` is specific to `speedymurmurs` and will select the highest nodes in the spanning tree. The second number in the tuple is how many nodes to select. |
| exp_path                      | file path. Is the path to any pre-runs that were needed. Pre-runs are needed for both the `by_tree_depth` and `by_number_of_transactions` cases.                                                                                                                                                                                                                                                                                                                                                                                         |
| force_overwrite               | boolean. See simulator configs.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |


### Stopping An Experiment
When the experiments are done running, stop the engines with `./sim
stop`; this must be run on all nodes in the cluster. You can see the current
status of your ipcluster by running `./sim status`.

### Configuring Concurrent Simulations On Multiple Machines
Open `~/.ipython/profile_default/security/ipcontroller-engine.json`, and set the
`location` key to the hostname where you want to run the controller. Copy this
configuration file to the same path on all machines in the cluster.

# Results Analysis
The output will be written to a series of subdirectories within `data/`. The
naming convention is as follows
`<simulation_type>-id<dataset_id>-<experiment_id>`. The results for that run are
a collection of `.txt` files under
`data/dynamic-..../READABLE_FILE.../0/CREDIT.../`. The files of interest are:

| Filename                       | Meaning                                                                              |
|- |- |
| cnet-nodeDepths.txt            | The depth of each node in the spanning tree. Only for `speedymurmurs`.               |
| cnet-numChildren.txt           | The number of children for each node in the spanning tree. Only for `speedymurmurs`. |
| cnet-succR.txt                 | The success ratio in each epoch.                                                     |
| cnet-totalCreditTransacted.txt | The successfully transacted values in each epoch.                                    |
| cnet-transactionPerNode.txt    | The number of transactions per node.                                                 |
