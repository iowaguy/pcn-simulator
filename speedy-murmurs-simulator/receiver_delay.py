import simulation_common as common

config = '''
attempts: 1
base: ../tiny-data/finalSets/dynamic
data_set_name: tiny-{data_set}
force_overwrite: false
iterations: 1
routing_algorithm: "{alg}"
simulation_type: dynamic
step: {step}
topology: jan2013-lcc-t0.graph
transaction_set: jan2013-trans-lcc-noself-uniq-{tran_set}.txt
new_links_path: jan2013-newlinks-lcc-sorted-uniq-t{data_set}.txt
trees: 3
concurrent_transactions: true
concurrent_transactions_count: 50
network_latency_ms: 171
log_level: warn
attack_properties:
    attack_type: receiver_delay
    receiver_delay_ms: {delay_ms}
'''

def generate_configs():
    config_set = []

    for delay_ms in range(0, 1001, 100):
        for step in range(0, 10):
            config_dict_list = []
            for alg in [common.speedymurmurs, common.silentwhispers]:
                for data_set in range(0, 9):
                    tran_set = data_set + 1

                    config_dict_list.append(common.parse_config(
                        config.format(data_set=data_set, tran_set=tran_set, alg=alg, step=step, delay_ms=delay_ms)))

            config_set.append(config_dict_list)

    return config_set
