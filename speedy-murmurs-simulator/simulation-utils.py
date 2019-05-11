#!/usr/local/bin/python3

import ipyparallel

global ipyclient
ipyclient = ipyparallel.Client()
with ipyclient[:].sync_imports(local=True):
    import numpy
    import matplotlib.pyplot
    import subprocess
    import networkx
    import os
    import shutil
    import yaml
    import ipyparallel
    from networkx import __version__ as networkxversion

print('networkx: '+networkxversion)
%matplotlib inline

## General utility functions and variables
global singles
global classpath
global ID
global silentwhispers
global speedymurmurs
global max_steps
global max_attempts
global max_trees


singles = '_singles.txt'
classpath = 'target/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies.jar'
ID = 'id'
silentwhispers = 'silentwhispers'
speedymurmurs = 'speedymurmurs'
maxflow = 'maxflow'
max_steps = 9
max_attempts = 10
max_trees = 7
config_file_name = 'runconfig.yml'
output_dir_default_base = 'data'

algo_info = {
    'silentwhispers':{
        'token':'SW-PER-MUL',
        'run_token':'SILENTW-false-true',
        'short_name':'SW',
        'id':0
    },
    'speedymurmurs':{
        'token':'V-DYN',
        'run_token':'TDRAP-true-false',
        'short_name':'SM',
        'id':7
    },
    'maxflow':{
        'short_name':'M',
        'id':10
    }
}
static_node_count = '67149'
dynamic_node_count = '93502'

data_root = 'data'
static_data_root = 'data/static/'
dynamic_data_root = f'{data_root}'

dynamic_epoch = '165552.45497208898'
static_epoch = '1000.0'


def get_static_data_path_config(config_dict):
    algo = config_dict["routing_algorithm"]
    dir = f'{config_dict["simulation_type"]}-{config_dict["data_set_name"]}-{algo}-{config_dict["trees"]}-{config_dict["attempts"]}-{config_dict["iterations"]}'
    if not config_dict["attack_properties"] == None and config_dict["attack_properties"]["attackers"] != 0:
        dir += f'-{config_dict["attack_properties"]["attack_type"]}-{config_dict["attack_properties"]["attacker_selection"]}-{config_dict["attack_properties"]["attackers"]}'

    dir2 = f'/READABLE_FILE_{algo_info[algo]["token"]}-{static_node_count}/0/CREDIT_NETWORK-STATIC-{static_epoch}-TREE_ROUTE_{algo_info[algo]["run_token"]}-{config_dict["trees"]}-2000.0-RANDOM_PARTITIONER-{config_dict["attempts"]}/'

    return (dir, dir2)

def get_static_data_path(algo, tree, attempts):
    return f'{static_data_root}/READABLE_FILE_{algo_info[algo]["token"]}-{static_node_count}/0/CREDIT_NETWORK-STATIC-{static_epoch}-TREE_ROUTE_{algo_info[algo]["run_token"]}-{tree}-2000.0-RANDOM_PARTITIONER-{attempts}'

def get_dynamic_data_path(algo, tree, attempts, step):
    step += 1
    retries = attempts - 1
    if algo == maxflow:
        return f'{dynamic_data_root}/READABLE_FILE_{algo_info[algo]["short_name"]}-P{step}-{dynamic_node_count}/0/CREDIT_MAX_FLOW-0.0-0'
    else:
        return f'{dynamic_data_root}{algo_info[algo]["short_name"]}-P{step}-{dynamic_node_count}/0/CREDIT_NETWORK-{algo_info[algo]["short_name"]}-P{step}-{dynamic_epoch}-TREE_ROUTE_{algo_info[algo]["run_token"]}-{tree}-331.10490994417796-RANDOM_PARTITIONER-{retries}'

def extract_kv_pairs_from_singles(singles_path):
    ret = {}
    with open(singles_path, 'r') as f:
        for line in f:
            k,v = line.rstrip().split('=')
            ret[k] = v
    return ret

def extract_from_singles(algo, attempts, trees, key):
    x_vs_key = []
    for tree in range(1,trees+1):
        data_path = get_static_data_path(algo, tree, attempts) + '/' + singles
        singles_pairs = extract_kv_pairs_from_singles(data_path)
        x_vs_key.append(float(singles_pairs[key]))
    return x_vs_key

# this will also calculate averages if there are multiple runs
def extract_from_singles_config(config_dict_list, key, sorting_key1=None, sorting_key2=None):
    x_vs_key = []
    buckets = {}

    # sort configs into buckets, where each bucket is the same except for the transaction set
    # the key for the buckets is the property identified by sorting keys 1 and 2
    for config_dict in config_dict_list:
        if sorting_key1 and sorting_key2:
            prop = config_dict[sorting_key1][sorting_key2]
        elif sorting_key1:
            prop = config_dict[sorting_key1]
        elif sorting_key2:
            raise Exception("ERROR: This should never be reached, dictionary is malformed")
        else:
            raise Exception("ERROR: Key does not exist in dictionary")

        if prop not in buckets:
            buckets[prop] = []

        buckets[prop].append(config_dict)
        # else:
        #     # if there is no sorting key, then add to list directly
        #     static_data_path = get_static_data_path_config(config_dict)
        #     data_path = os.getcwd() + f'/{data_root}/' + static_data_path[0] + static_data_path[1] + singles
        #     singles_pairs = extract_kv_pairs_from_singles(data_path)
        #     x_vs_key.append(float(singles_pairs[key]))

    for bucket in buckets:
        vals = []
        for config_dict in buckets[bucket]:
            static_data_path = get_static_data_path_config(config_dict)
            data_path = os.getcwd() + f'/{data_root}/' + static_data_path[0] + static_data_path[1] + singles
            singles_pairs = extract_kv_pairs_from_singles(data_path)
            vals.append(float(singles_pairs[key]))
        x_vs_key.append(numpy.mean(vals))

    return x_vs_key

def extract_from_singles_attempts(algo, num_attempts, trees, key):
    x_vs_key = []
    for attempts in range(1,num_attempts+1):
        data_path = get_static_data_path(algo, trees, attempts) + '/' + singles
        singles_pairs = extract_kv_pairs_from_singles(data_path)
        x_vs_key.append(float(singles_pairs[key]))
    return x_vs_key

def convert_kv_file_to_dict(filepath):
    out_dict = {}
    with open(filepath, 'r') as textfile:
        for line in textfile:
            k,v = line.rstrip().split('\t')
            out_dict[int(k)] = float(v)
    return out_dict

def dict_to_list(d, xrange):
    out_list = []
    for i in range(1,xrange):
        if i in d:
            out_list.append(d[i])
        else:
            out_list.append(0)
    return out_list

# repeated values are ignored
def merge_dicts(dict1, dict2):
    data_dict = {}
    for k, v in dict1.items():
        if k in dict2:
            data_dict[k] = dict2[k]
        else:
            data_dict[k] = dict1[k]
    return data_dict

def create_output_dir(config_dict):
    dir = os.getcwd() + f'/{output_dir_default_base}/' + get_static_data_path_config(config_dict)[0]
    if not os.path.isdir(dir):
        os.makedirs(dir)
        return dir
    elif config_dict["force_overwrite"]:
        shutil.rmtree(dir, ignore_errors=True)
        os.makedirs(dir)
        return dir

def get_epoch_length(transactions_file):
    transactions_list = []
    for i in range(1, 10):
        transactions_list = read_transactions_file(transactions_file.format(i), transactions_list)

    # sum the time between subsequent transctions
    sum_delta = 0
    for i in range(1, len(transactions_list)):
        sum_delta += transactions_list[i][0] - transactions_list[i - 1][0]
    delta_av = sum_delta/len(transactions_list)
    return (delta_av * 1000, transactions_list)

def read_link_changes_files(link_changes_file):
    link_changes_list = []
    for i in range(1, 10):
        link_changes_list = read_link_changes_file(link_changes_file.format(i), link_changes_list)
    return link_changes_list

def read_link_changes_file(link_changes_file, link_changes_list = []):
    with open(link_changes_file, 'r') as link_changes:
        count = 0
        for link_change in link_changes:
            lc = link_change.split(" ")
            # time, source, destination, amount
            link_changes_list.append((int(lc[0]), lc[1], lc[2], lc[3]))

        return link_changes_list

def calculate_events_per_epoch(epoch_length, events):
    cur_epoch = 1
    next_epoch_starts = epoch_length

    events_in_current_epoch = 0
    events_per_epoch = {}
    for event in events:
        if event[0] < next_epoch_starts:
            events_in_current_epoch += 1
        else:
            events_per_epoch[cur_epoch] = events_in_current_epoch
            next_epoch_starts += epoch_length
            cur_epoch += 1
            # should be 1 because it needs to include the current transaction
            events_in_current_epoch = 1
    return events_per_epoch

def read_transactions_file(transactions_file, transactions_list = []):
    with open(transactions_file, 'r') as transactions:
        count = 0
        for transaction in transactions:
            t = transaction.split(" ")
            if len(t) == 4:
                # time, amount, source, destination
                transactions_list.append((int(t[0]), t[1], t[2], t[3]))
            elif len(t) == 3:
                # time, amount, source, destination
                transactions_list.append((count, t[1], t[2], t[3]))
                count += 1

        return transactions_list

## Run a single simulation
def run_static(transaction_set, algo, attempts, trees, attack, force=False):
    # skip run if it has already happened
    if not force and os.path.isdir(get_static_data_path(algo, trees, attempts)):
        print('Run exists. Skipping...')
        return
    print(f'Running: java -cp {classpath} treeembedding.tests.Static {transaction_set} {algo_info[algo][ID]} {attempts} {trees} {attack}')
    subprocess.run(['java', '-cp', f'{classpath}', 'treeembedding.tests.Static', f'{transaction_set}', f'{algo_info[algo][ID]}', f'{attempts}', f'{trees}', f'{attack}'], capture_output=True)

def run_dynamic(transaction_set, algo, attempts, trees, step, force=False):
    # skip run if it has already happened
    if not force and os.path.isdir(get_dynamic_data_path(algo, trees, attempts, step)):
        print('Run exists. Skipping...')
        return
    else:
        print(f'Running: java -cp {classpath} treeembedding.tests.Dynamic {transaction_set} {algo_info[algo][ID]} {step}')
        subprocess.run(['java', '-cp', f'{classpath}', 'treeembedding.tests.Dynamic', f'{transaction_set}', f'{algo_info[algo][ID]}', f'{step}'], capture_output=True)

def run_static_config(config_dict, output_dir, force=False):
    # import pdb; pdb.set_trace()
    # skip run if it has already happened
    if not force and os.path.isdir(get_static_data_path(config_dict['routing_algorithm'], config_dict['trees'], config_dict['attempts'])):
        print('Run exists. Skipping...')
        return
    print(f'Running: java -cp {classpath} treeembedding.tests.Static {output_dir}')
    subprocess.run(['java', '-cp', f'{classpath}', 'treeembedding.tests.Static', f'{output_dir}'], capture_output=True)

def run_dynamic_config(transaction_set, algo, attempts, trees, step, force=False):
    # skip run if it has already happened
    if not force and os.path.isdir(get_dynamic_data_path(algo, trees, attempts, step)):
        print('Run exists. Skipping...')
        return
    else:
        print(f'Running: java -cp {classpath} treeembedding.tests.Dynamic {transaction_set} {algo_info[algo][ID]} {step}')
        subprocess.run(['java', '-cp', f'{classpath}', 'treeembedding.tests.Dynamic', f'{transaction_set}', f'{algo_info[algo][ID]}', f'{step}'], capture_output=True)

def parse_config(config_text):
    try:
        return yaml.safe_load(config_text)
    except yaml.YAMLError as exc:
        print(exc)

def do_experiment(config_dict):

    output_dir = create_output_dir(config_dict)
    return output_dir
    if output_dir == None:
        # don't perform a run
        print("Not performing run.")
        return

    # store a copy of the config in the directory
    config_file_path = output_dir + '/' + config_file_name
    with open(config_file_path, "w") as f:
        f.write(yaml.dump(config_dict))

    if config_dict["simulation_type"] == 'static':
        run_static_config(config_dict, output_dir, config_dict['force_overwrite'])
    elif config_dict["simulation_type"] == 'dynamic':
        run_dynamic(config_dict, output_dir)

def do_experiments(config_dict_list):
    ipyclient[:].push(dict(create_output_dir=create_output_dir, output_dir_default_base=output_dir_default_base, get_static_data_path_config=get_static_data_path_config, algo_info=algo_info, static_node_count=static_node_count, static_epoch=static_epoch, config_file_name=config_file_name, run_static_config=run_static_config, get_static_data_path=get_static_data_path, static_data_root=static_data_root, classpath=classpath))
    a = ipyclient[:].map_async(do_experiment, config_dict_list)

    for i,r in enumerate(a):
        print(f"task {i}; something: {r}")

