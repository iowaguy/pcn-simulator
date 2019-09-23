#!/usr/local/bin/python3

singles = '_singles.txt'
classpath = 'target/pcn-simulator-1.0-SNAPSHOT-jar-with-dependencies.jar'
ID = 'id'
silentwhispers = 'silentwhispers'
speedymurmurs = 'speedymurmurs'
maxflow = 'maxflow_collateralize'
max_steps = 9
max_attempts = 10
max_trees = 7
config_file_name = 'runconfig.yml'
data_root = 'data'

algo_info = {
    'silentwhispers': {
        'token': 'SW-PER-MUL',
        'run_token': 'SILENTW-false-true',
        'short_name': 'SW',
        'id': 0
    },
    'speedymurmurs': {
        'token': 'V-DYN',
        'run_token': 'TDRAP-true-false',
        'short_name': 'SM',
        'id': 7
    },
    'maxflow': {
        'short_name': 'M',
        'id': 10
    },
    'maxflow_collateralize': {
        'short_name': 'M',
        'id': 10
    }    
}

run_info = {
    'static': {
        'node_count': '67149',
        'data_root': f'{data_root}/static/',
        'epoch': '1000.0',
        'class': 'treeembedding.runners.Static'
    },
    'dynamic': {
        'node_count': '93502',
        'data_root': data_root,
        'epoch': '165552.45497208898',
        'class': 'treeembedding.runners.Dynamic'
    }
}

def get_output_base_path(config_dict):
    algo = config_dict["routing_algorithm"]

    data_set_size = config_dict["data_set_name"].split("-")[0]
    dir = f'{config_dict["simulation_type"]}-{data_set_size}-{config_dict["experiment_name"]}/{config_dict["simulation_type"]}-{config_dict["data_set_name"]}-{algo}-{config_dict["trees"]}-{config_dict["attempts"]}-{config_dict["iterations"]}-lat{config_dict["network_latency_ms"]}ms'

    if config_dict["concurrent_transactions"]:
        dir += f'-concurrent-{config_dict["concurrent_transactions_count"]}'

    if "attack_properties" in config_dict:
        if config_dict["attack_properties"]["attackers"] != 0:
            dir += f'-{config_dict["attack_properties"]["attack_type"]}-{config_dict["attack_properties"]["attacker_selection"]}-{config_dict["attack_properties"]["attackers"]}'
        if config_dict["attack_properties"]["receiver_delay_ms"] != 0:
            dir += f'-{config_dict["attack_properties"]["attack_type"]}-{config_dict["attack_properties"]["receiver_delay_ms"]}'

    return dir


def get_dynamic_data_path_config(config_dict):
    algo = config_dict["routing_algorithm"]
    count = config_dict['step']

    dir = f'/READABLE_FILE_{algo_info[algo]["short_name"]}-P{count}-{run_info["dynamic"]["node_count"]}'

    if algo == maxflow:
        dir2 = f'/0/CREDIT_MAX_FLOW-0.0-0/'
    else:
        dir2 = f'/0/CREDIT_NETWORK-{algo_info[algo]["short_name"]}-P{count}-{run_info["dynamic"]["epoch"]}-TREE_ROUTE_{algo_info[algo]["run_token"]}-{config_dict["trees"]}-331.10490994417796-RANDOM_PARTITIONER-{config_dict["attempts"]}/'

    return (dir, dir2)


def get_static_data_path_config(config_dict):
    algo = config_dict["routing_algorithm"]
    dir = f'/READABLE_FILE_{algo_info[algo]["token"]}-{run_info["static"]["node_count"]}'
    dir2 = f'/0/CREDIT_NETWORK-STATIC-{run_info["static"]["epoch"]}-TREE_ROUTE_{algo_info[algo]["run_token"]}-{config_dict["trees"]}-2000.0-RANDOM_PARTITIONER-{config_dict["attempts"]}/'

    return (dir, dir2)


def create_output_dir(config_dict):
    import os
    import shutil
    dir = os.getcwd() + f'/{data_root}/' + get_output_base_path(config_dict)
    if not os.path.isdir(dir):
        os.makedirs(dir)
    elif config_dict["force_overwrite"]:
        shutil.rmtree(dir, ignore_errors=True)
        os.makedirs(dir)
    return dir

def parse_config(config_text):
    import yaml
    try:
        return yaml.safe_load(config_text)
    except yaml.YAMLError as exc:
        print(exc)
