import simulation_common as common
import attack_common

config = '''
attempts: 1
base: ../data-synthetic/id2-nodes-100k-txs-1m-scalefree
data_set_name: full-synthetic-id2
experiment_name: dynamic-baseline-sequential-maxflow-no-conc
force_overwrite: false
step: {step}
iterations: 1
routing_algorithm: "{alg}"
simulation_type: dynamic
topology: topology.graph
transaction_set: transactions-{tran_set}.txt
trees: 3
new_links_path: newlinks.txt
concurrent_transactions: false
concurrent_transactions_count: 1
network_latency_ms: 0
epoch_length: 1250
log_level: warn
'''

# epoch length is 1M/800

def generate_configs():

    config_dict_list = []
    for step in range(0, 10):
        l = []
        for alg in [common.maxflow]:
            tran_set = step + 1
            l.append(common.parse_config(
                config.format(alg=alg, tran_set=tran_set, step=step)))
        config_dict_list.append(l)
    return config_dict_list


if __name__ == "__main__":
    configs = generate_configs()
    attack_common.start(configs)
