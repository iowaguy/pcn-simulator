import sys
import ipyparallel
import simulation_common
import attack_1 as attack1
import dynamic_latency_baseline as dlb

def run_config(config_dict, output_dir, force=False):
    import os
    import subprocess
    import shutil
    import simulation_common

    base = os.getcwd() + f'/{simulation_common.data_root}/' + simulation_common.get_output_base_path(config_dict)

    data_path = None
    if config_dict['simulation_type'] == "static":
        data_path = simulation_common.get_static_data_path_config(config_dict)
    elif config_dict['simulation_type'] == "dynamic":
        data_path = simulation_common.get_dynamic_data_path_config(config_dict)
    else:
        return f"Invalid simulation type: {config_dict['simulation_type']}"

    path = base + data_path[0] + data_path[1]
    # skip run if it has already happened
    if not force and os.path.isfile(path + simulation_common.singles):
        return 'Run exists. Skipping...'

    algo = config_dict['routing_algorithm']

    # if directory exists without file, then delete the directory
    if os.path.isdir(base + data_path[0]): ## TODO why isn't old path getting deleted?
        if not os.path.isfile(path + simulation_common.singles):
            shutil.rmtree(base + data_path[0], ignore_errors=True)

    sim_type = config_dict['simulation_type']
    out = subprocess.run(['java', '-cp', f'{simulation_common.classpath}', f'{simulation_common.run_info[sim_type]["class"]}', f'{output_dir}'], capture_output=True)
    # return str(out) + str(config_dict)

    # if it fails, delete dir
    if out.returncode == 1:
        shutil.rmtree(base + data_path[0], ignore_errors=True)
        # out = "Rerun: " + str(subprocess.run(['java', '-cp', f'{simulation_common.classpath}', f'{simulation_common.run_info[sim_type]["class"]}', f'{output_dir}'], capture_output=True))

    # this is what get's printed by ipyparallel
    return str(out) + str(config_dict)

def setup():
    import sys
    import os
    sys.path.append(os.getcwd())

def do_experiment(config_dict):
    import yaml
    import simulation_common
    import sys

    output_dir = simulation_common.create_output_dir(config_dict)

    # store a copy of the config in the directory
    config_file_path = output_dir + '/' + simulation_common.config_file_name
    with open(config_file_path, "w") as f:
        f.write(yaml.dump(config_dict))

    return attack_common.run_config(config_dict, output_dir, config_dict['force_overwrite'])

def do_distributed_experiments(ipyclient, config_dict_list):
    lbv = ipyclient.load_balanced_view()
    result = lbv.map_sync(do_experiment, config_dict_list)

    # dview = ipyclient[:]
    # result = dview.map_sync(do_experiment, config_dict_list)

    for i, r in enumerate(result):
        print(f"Task ID #{i}; Command: {r}", flush=True)

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
        config_dict_sets = attack1.generate_configs()
    elif (attack_type == 'baseline'):
        config_dict_sets = dlb.generate_configs()
    else:
        print(f'Error: Not a valid attack type: {attack_type}')
        sys.exit()

    for config_list in config_dict_sets:
        do_distributed_experiments(ipyclient, config_list)

    end = time.time()
    elapsed = end - start
    print(f"Done. Time: {elapsed}")

if __name__ == '__main__':
    start(sys.argv[1])
