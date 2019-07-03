import sys
import ipyparallel
import simulation_common
import attack_1 as attack1
import dynamic_latency_baseline as dlb

# these will hold the configs that are used in the simulations

## Run a single simulation
# def run_static(transaction_set, algo, attempts, trees, attack, force=False):
#     import os
#     import simulation_common

#     # skip run if it has already happened
#     if not force and os.path.isdir(simulation_common.get_static_data_path(algo, trees, attempts)):
#         print('Run exists. Skipping...')
#         return
#     print(f'Running: java -cp {simulation_common.classpath} treeembedding.runners.Static {transaction_set} {simulation_common.algo_info[algo][ID]} {attempts} {trees} {attack}')
#     subprocess.run(['java', '-cp', f'{simulation_common.classpath}', 'treeembedding.runners.Static', f'{transaction_set}',
#                     f'{simulation_common.algo_info[algo][ID]}', f'{attempts}', f'{trees}', f'{attack}'])


# def run_dynamic(transaction_set, algo, attempts, trees, step, force=False):
#     import os
#     import simulation_common

#     # skip run if it has already happened
#     if not force and os.path.isdir(get_dynamic_data_path(algo, trees, attempts, step)):
#         print('Run exists. Skipping...')
#         return
#     else:
#         print(f'Running: java -cp {simulation_common.classpath} treeembedding.runners.Dynamic {transaction_set} {simulation_common.algo_info[algo][simulation_common.ID]} {step}')
#         subprocess.run(['java', '-cp', f'{simulation_common.classpath}', 'treeembedding.runners.Dynamic', f'{transaction_set}',
#                         f'{simulation_common.algo_info[algo][simulation_common.ID]}', f'{step}'])


# def run_dynamic_config(config_dict, output_dir, force=False):
#     import os
#     import subprocess
#     import shutil
#     import simulation_common

#     # skip run if it has already happened
#     if not force and os.path.isfile(
#             simulation_common.get_dynamic_data_path(config_dict['routing_algorithm'], config_dict['trees'],
#                                                     config_dict['attempts'], config_dict['step']) + '/' + simulation_common.singles):
#         print('Run exists. Skipping...')
#         return 'Run exists. Skipping...'

#     algo = config_dict['routing_algorithm']
#     base = os.getcwd() + f'/{simulation_common.data_root}/' + simulation_common.get_output_base_path(config_dict)
#     dynamic_data_path = simulation_common.get_dynamic_data_path_config(config_dict)

#     # if directory exists without file, then delete the directory
#     if os.path.isdir(base + dynamic_data_path[0]):
#         path = base + dynamic_data_path[0] + '/' + dynamic_data_path[1]
#         if not os.path.isfile(path + simulation_common.singles):
#             shutil.rmtree(path)

#     print(f'Running: java -cp {simulation_common.classpath} {simulation_common.run_info["dynamic"]["class"]} {output_dir}')
#     subprocess.run(['java', '-cp', f'{simulation_common.classpath}', f'{simulation_common.run_info["dynamic"]["class"]}', f'{output_dir}'])
#     return f'java -cp {simulation_common.classpath} {simulation_common.run_info["dynamic"]["class"]} {output_dir}'


def run_config(config_dict, output_dir, force=False):
    import os
    import subprocess
    import shutil
    import simulation_common

    # skip run if it has already happened
    if not force:
        tmp_path = None
        if config_dict['simulation_type'] == "static":
            tmp_path = simulation_common.get_static_data_path(config_dict['routing_algorithm'], config_dict['trees'],
                                                              config_dict['attempts'])
        elif config_dict['simulation_type'] == "dynamic":
            tmp_path = simulation_common.get_dynamic_data_path(config_dict['routing_algorithm'], config_dict['trees'],
                                                               config_dict['attempts'], config_dict['step'])
        else:
            print(f"Invalid simulation type: {config_dict['simulation_type']}")
            return f"Invalid simulation type: {config_dict['simulation_type']}"

        if os.path.isfile(tmp_path + '/' + simulation_common.singles):
            print('Run exists. Skipping...')
            return 'Run exists. Skipping...'

    algo = config_dict['routing_algorithm']
    base = os.getcwd() + f'/{simulation_common.data_root}/' + simulation_common.get_output_base_path(config_dict)

    data_path = None
    if config_dict['simulation_type'] == "static":
        data_path = simulation_common.get_static_data_path_config(config_dict)
    elif config_dict['simulation_type'] == "dynamic":
        data_path = simulation_common.get_dynamic_data_path_config(config_dict)
    else:
        print(f"Invalid simulation type: {config_dict['simulation_type']}")
        return f"Invalid simulation type: {config_dict['simulation_type']}"

    # if directory exists without file, then delete the directory
    if os.path.isdir(base + data_path[0]):
        path = base + data_path[0] + '/' + data_path[1]
        if not os.path.isfile(path + simulation_common.singles):
            shutil.rmtree(path)

    sim_type = config_dict['simulation_type']
    print(f'Running: java -cp {simulation_common.classpath} {simulation_common.run_info[sim_type]["class"]} {output_dir}')
    subprocess.run(['java', '-cp', f'{simulation_common.classpath}', f'{simulation_common.run_info[sim_type]["class"]}', f'{output_dir}'])
    return f'java -cp {simulation_common.classpath} {simulation_common.run_info[sim_type]["class"]} {output_dir}'

# def run_dynamic_config(transaction_set, algo, attempts, trees, step, force=False):
#     import os
#     import subprocess
#     import simulation_common

#     # skip run if it has already happened
#     if not force and os.path.isfile(
#             get_dynamic_data_path(algo, trees, attempts, step) + '/' + simulation_common.singles):
#         print('Run exists. Skipping...')
#         return 'Run exists. Skipping...'
#     else:
#         print(f'Running: java -cp {simulation_common.classpath} treeembedding.runners.Dynamic {transaction_set} {simulation_common.algo_info[algo][simulation_common.ID]} {step}')
#         subprocess.run(
#             ['java', '-cp', f'{simulation_common.classpath}', 'treeembedding.runners.Dynamic', f'{transaction_set}',
#              f'{simulation_common.algo_info[algo][simulation_common.ID]}', f'{step}'])
#         return f'java -cp {simulation_common.classpath} treeembedding.runners.Dynamic {transaction_set} {simulation_common.algo_info[algo][simulation_common.ID]} {step}'

def setup():
    import sys
    import os
    sys.path.append(os.getcwd())

def do_experiment(config_dict):
    import yaml
    import simulation_common
    output_dir = simulation_common.create_output_dir(config_dict)

    # store a copy of the config in the directory
    config_file_path = output_dir + '/' + simulation_common.config_file_name
    with open(config_file_path, "w") as f:
        f.write(yaml.dump(config_dict))

    return attack_common.run_config(config_dict, output_dir, config_dict['force_overwrite'])

def do_distributed_experiments(ipyclient, config_dict_list):
    lbv = ipyclient.load_balanced_view()
    result = lbv.map_sync(do_experiment, config_dict_list)

    for i, r in enumerate(result):
        print(f"Task ID #{i}; Command: {r}")

def start(attack_type):
    import time
    import ipyparallel

    start = time.time()
    ipyclient = ipyparallel.Client()
    ipyclient[:].apply_sync(setup)

    with ipyclient[:].sync_imports(local=True):
        import simulation_utils
        import simulation_common
        import sys
        import os
        import ipyparallel
        import attack_common
        from networkx import __version__ as networkxversion
    print('networkx: ' + networkxversion)

    config_dict_list_sm = []
    config_dict_list_sw = []
    print(f"attack type: {attack_type}")
    if (attack_type == '1'):
        config_dict_list_sm, config_dict_list_sw = attack1.generate_configs()
    elif (attack_type == 'baseline'):
        config_dict_list_sm, config_dict_list_sw = dlb.generate_configs()
    else:
        print(f'Error: Not a valid attack type: {attack_type}')
        sys.exit()

    do_distributed_experiments(ipyclient, config_dict_list_sm)
    do_distributed_experiments(ipyclient, config_dict_list_sw)
    end = time.time()
    elapsed = end - start
    print(f"Done. Time: {elapsed}")

if __name__ == '__main__':
    start(sys.argv[1])
