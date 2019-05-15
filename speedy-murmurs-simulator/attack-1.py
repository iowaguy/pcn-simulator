import ipyparallel
import simulation_utils as su

config_dict_list_srvna_sm = []
config_dict_list_srvna_sw = []

def setup():
    import sys
    import os
    sys.path.append(os.getcwd())


def generate_configs():
    for data_set in range(0, 10):
        for alg in [su.speedymurmurs, su.silentwhispers]:
            # for alg in [silentwhispers]:
            for attackers in range(0, 30001, 5000):
                # for attackers in range(30000, 30001, 5000):
                config = f'''
data_set_name: tiny-{data_set}
base: ../tiny-data/finalSets/static
topology: ripple-lcc.graph
link_weights: ripple-lcc.graph_CREDIT_LINKS
transaction_set: sampleTr-{data_set}.txt
simulation_type: static
force_overwrite: true
routing_algorithm: "{alg}"
attempts: 1
trees: 3
attack_properties:
    attackers: {attackers}
    attacker_selection: "random"
    attack_type: drop_all
iterations: 1
'''
                if alg == su.speedymurmurs:
                    config_dict_list_srvna_sm.append(su.parse_config(config))
                elif alg == su.silentwhispers:
                    config_dict_list_srvna_sw.append(su.parse_config(config))

def do_experiments(config_dict_list):
    lbv = ipyclient.load_balanced_view()
    result = lbv.map_sync(simulation_utils.do_experiment, config_dict_list)

    for i,r in enumerate(result):
        print(f"Task ID #{i}; Command: {r}")


if __name__ == '__main__':
    ipyclient = ipyparallel.Client()
    ipyclient[:].apply_sync(setup)

    with ipyclient[:].sync_imports(local=True):
        import simulation_utils
        import sys
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

    generate_configs()
    do_experiments(config_dict_list_srvna_sm)
    do_experiments(config_dict_list_srvna_sw)
    print("Done.")
