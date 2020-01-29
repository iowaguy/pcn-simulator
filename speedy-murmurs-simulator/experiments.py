import simulation_common as common
import attack_common
import sys

config = '''
attempts: {attempts}
base: ../../pcn-topologies/datasets/{data_set}
data_set_name: {data_set}
experiment_name: {experiment_name}
force_overwrite: {force_overwrite}
step: {step}
iterations: {iterations}
routing_algorithm: "{alg}"
simulation_type: {simulation_type}
topology: topology.graph
transaction_set: transactions-{tran_set}.txt
trees: {trees}
new_links_path: newlinks-{new_links}.txt
concurrent_transactions: {concurrent_transactions}
concurrent_transactions_count: {concurrent_transactions_count}
network_latency_ms: {network_latency_ms}
epoch_length: {epoch}
log_level: warn
'''

attack_config = '''
attack_properties:
  attack_type: {attack_type}
  attacker_selection: {attacker_selection}
  attackers: {attackers}
  receiver_delay_ms: {attack_delay}
'''

# epoch_lengths_list: a list of the epoch lengths for each data set, indexes should match. See RunConfig.java to learn what the properties mean.
def generate_configs(data_set_list, routing_algorithms, epoch_lengths_list, experiment_name,
                     attempts=1, num_steps=8, force_overwrite=False, iterations=1,
                     simulation_type="dynamic", trees=3, concurrent_transactions_count=1,
                     network_latency_ms=0, attack_type=None, attacker_selection=None,
                     attackers=0, attack_delay=0):

    # if there is only one transaction at a time, transactions are not concurrent
    concurrent_transactions = concurrent_transactions_count != 1
    config_dict_list = []
    for step in range(0, num_steps+1):
        l = []
        for i in range(0, len(data_set_list)):
            for alg in routing_algorithms:
                tran_set = step + 1
                config_formatted = config.format(alg=alg,
                                  tran_set=tran_set,
                                  step=step,
                                  data_set=data_set_list[i],
                                  epoch=epoch_lengths_list[i],
                                  new_links=tran_set,
                                  attempts=attempts,
                                  experiment_name=experiment_name,
                                  force_overwrite=force_overwrite,
                                  iterations=iterations,
                                  simulation_type=simulation_type,
                                  trees=trees,
                                  concurrent_transactions=concurrent_transactions,
                                  concurrent_transactions_count=concurrent_transactions_count,
                                  network_latency_ms=network_latency_ms)
                if attack_type:
                    attack_config = attack_config.format(attack_type=attack_type,
                                                         attacker_selection=attacker_selection,
                                                         attackers=attackers,
                                                         attack_delay=attack_delay)
                    config_formatted+=attack_config

                l.append(common.parse_config(config_formatted))
        config_dict_list.append(l)
    return config_dict_list

def get_experiments():
    experiments = {
        "test" : {
            "data_set_list":["id9-synthetic-nodes-1k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        #############################
        "dynamic_baseline_sequential" : {
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree",
                             "id2-synthetic-nodes-100k-txs-1m-scalefree",
                             "id3-measured-nodes-93502-txs-1m-ripple-dynamic"],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs,
                                  common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250, 1250, 165552.45497208898],
        },
        #############################
        "dynamic-baseline-sequential-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-10-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":10,
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":25,
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":50,
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-100-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":100,
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        ##############################
        "dynamic-baseline-sequential-single-step-id8-mfct" : {
            "num_steps":1,
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-100-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":100,
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":50,
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":25,
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-10-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":10,
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        #############################
        "dynamic-baseline-sequential-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-100-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":100,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":50,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":25,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-10-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":10,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        ########################
        "dynamic-baseline-sequential-single-step-id0-mfct" : {
            "num_steps":1,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-100-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":100,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":50,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":25,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-10-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":10,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        }
    }

    return experiments

def get_experiment_config(exp_name):
    return generate_configs(experiment_name=exp_name, **get_experiments().get(exp_name))

if __name__ == "__main__":
    exp_name = sys.argv[1]

    if exp_name == "list":
        for k in get_experiments():
            print(k)
        exit()

    configs = get_experiment_config(exp_name)
    attack_common.start(configs)