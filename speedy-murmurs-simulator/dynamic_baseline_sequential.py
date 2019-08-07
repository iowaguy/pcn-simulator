import simulation_common as common
import attack_common

config = '''
attempts: 1
base: ../data/finalSets/dynamic
data_set_name: full-{data_set}
experiment_name: dynamic-baseline-sequential
force_overwrite: false
step: {step}
iterations: 1
routing_algorithm: "{alg}"
simulation_type: dynamic
topology: jan2013-lcc-t0.graph
transaction_set: jan2013-trans-lcc-noself-uniq-{tran_set}.txt
trees: 3
new_links_path: jan2013-newlinks-lcc-sorted-uniq-t{data_set}.txt
concurrent_transactions: true
concurrent_transactions_count: 1
network_latency_ms: 0
log_level: warn
'''

def generate_configs():

    config_dict_list = []
    for step in range(0, 10):
        l = []
        for alg in [common.speedymurmurs, common.silentwhispers]:
            data_set = step
            tran_set = data_set + 1
            l.append(common.parse_config(
                config.format(data_set=data_set, alg=alg, tran_set=tran_set, step=step)))
        config_dict_list.append(l)
    return config_dict_list


if __name__ == "__main__":
    configs = generate_configs()
    attack_common.start(configs)
