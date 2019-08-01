import simulation_common as common
import attack_common

config = '''
attempts: 1
base: ../data/finalSets/static
data_set_name: full-{data_set}
force_overwrite: false
iterations: 1
routing_algorithm: "{alg}"
simulation_type: static
topology: ripple-lcc.graph
transaction_set: sampleTr-{data_set}.txt
trees: 3
concurrent_transactions: true
concurrent_transactions_count: 20
network_latency_ms: 30
log_level: warn
attack_properties:
  attack_type: griefing
  attacker_selection: random
  attackers: {num_attackers}
  receiver_delay_ms: 1000
'''

def generate_configs():

    config_dict_list = [[]]
    for num_attackers in [10**x for x in range(0, 5)]:
        for alg in [common.speedymurmurs, common.silentwhispers]:
            for data_set in range(0, 10):
                config_dict_list[0].append(common.parse_config(
                    config.format(data_set=data_set, alg=alg, num_attackers=num_attackers)))

    return config_dict_list


if __name__ == "__main__":
    configs = generate_configs()
    attack_common.start(configs)
