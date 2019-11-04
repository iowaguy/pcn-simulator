import simulation_common as common
import attack_common

config = '''
attempts: 1
base: ../data/{data_set}
data_set_name: {data_set}
experiment_name: dynamic-baseline-sequential
force_overwrite: false
step: {step}
iterations: 1
routing_algorithm: "{alg}"
simulation_type: dynamic
topology: topology.graph
transaction_set: transactions-{tran_set}.txt
trees: 3
new_links_path: newlinks-{new_links}.txt
concurrent_transactions: false
concurrent_transactions_count: 1
network_latency_ms: 0
epoch_length: {epoch}
log_level: warn
'''

# epoch length is 1M/800 for synthetic data sets
epochs = [1250, 1250, 165552.45497208898]
data_set = ["id0-synthetic-nodes-100k-txs-1m-scalefree", "id2-synthetic-nodes-100k-txs-1m-scalefree", "id3-ripple-dynamic"]

def generate_configs():
    config_dict_list = []
    for step in range(0, 10):
        l = []
        for i in range(0, len(data_set)):
            for alg in [common.maxflow, common.speedymurmurs, common.silentwhispers]:
                tran_set = step + 1
                l.append(common.parse_config(
                    config.format(alg=alg, tran_set=tran_set, step=step, data_set=data_set[i], epoch=epochs[i], new_links=tran_set)))
        config_dict_list.append(l)
    return config_dict_list


if __name__ == "__main__":
    configs = generate_configs()
    # for c in configs:
    #     print(c)
    # exit()
    attack_common.start(configs)
