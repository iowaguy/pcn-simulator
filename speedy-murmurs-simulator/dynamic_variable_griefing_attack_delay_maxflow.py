import simulation_common as common
import attack_common

config = '''
attempts: 1
base: ../data/finalSets/dynamic
data_set_name: full
force_overwrite: false
experiment_name: dynamic-variable-griefing-delay
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
network_latency_ms: 30
log_level: warn
attack_properties:
  attack_type: griefing
  attacker_selection: random
  attackers: 1000
  receiver_delay_ms: {delay}
'''

def generate_configs():
    config_dict_list = []
    for step in range(0, 9):
        l = []
        for delay in range(0, 5001, 1000):
            for alg in [common.maxflow]:
                l.append(common.parse_config(
                    config.format(data_set=step, alg=alg, delay=delay, tran_set=(step + 1), step=step)))
        config_dict_list.append(l)
    return config_dict_list


if __name__ == "__main__":
    configs = generate_configs()
    attack_common.start(configs)