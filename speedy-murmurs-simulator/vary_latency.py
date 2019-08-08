import simulation_common as common
import attack_common

config = '''
attempts: 1
base: ../data/finalSets/dynamic
data_set_name: full
experiment_name: variable-latency
force_overwrite: false
step: {step}
iterations: 1
routing_algorithm: "{alg}"
simulation_type: dynamic
topology: jan2013-lcc-t0.graph
transaction_set: jan2013-trans-lcc-noself-uniq-{tran_set}.txt
new_links_path: jan2013-newlinks-lcc-sorted-uniq-t{data_set}.txt
trees: 3
concurrent_transactions: true
concurrent_transactions_count: 20
network_latency_ms: {latency}
log_level: warn
'''

def generate_configs():

    config_dict_list = []
    for step in range(0, 9):
        l = []
        for latency in range(0, 101, 20):
            for alg in [common.speedymurmurs, common.silentwhispers]:
                l.append(common.parse_config(
                    config.format(data_set=step, alg=alg, latency=latency, tran_set=(step + 1), step=step)))
        config_dict_list.append(l)
    return config_dict_list


if __name__ == "__main__":
    configs = generate_configs()
    attack_common.start(configs)
