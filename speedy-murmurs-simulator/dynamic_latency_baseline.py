import simulation_common as common

config = '''
attempts: 1
base: ../abbreviated-data/finalSets/dynamic
data_set_name: abbreviated-{data_set}
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
network_latency_ms: {latency}
log_level: warn
'''

def generate_configs():
    config_set = []

    for step in range(0, 10):
        config_dict_list = []
        for alg in [common.speedymurmurs, common.silentwhispers]:
            for latency in range(10, 101, 20):
                data_set = 0
                tran_set = data_set + 1

                config_dict_list.append(common.parse_config(
                    config.format(data_set=data_set, tran_set=tran_set, alg=alg, step=step, latency=latency)))

        config_set.append(config_dict_list)

    return config_set
