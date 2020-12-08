import simulation_common as common
import attack_common
import sys
import yaml
import json
import pcn
import pandas as pd
import re

config = '''
notes: {notes}
attempts: {attempts}
base: pcn-topologies/datasets/{data_set_name}
data_set_name: {data_set_name}
experiment_name: {experiment_name}
force_overwrite: {force_overwrite}
step: 0
iterations: {iterations}
routing_algorithm: "{routing_algorithm}"
simulation_type: {simulation_type}
topology: topology.graph
transaction_set: transactions.txt
trees: {trees}
new_links_path: newlinks.txt
concurrent_transactions: {concurrent_transactions}
concurrent_transactions_count: {concurrent_transactions_count}
network_latency_ms: {network_latency_ms}
epoch_length: {epoch_length}
jvm_options: {jvm_options}
receiver_delay_variability: {receiver_delay_variability}
arrival_delay_ms: {arrival_delay_ms}
log_level: error
'''

attack_config = '''
attack_properties:
  attack_type: {attack_type}
  attacker_selection: {attacker_selection}
  receiver_delay_ms: {receiver_delay_ms}
'''

selected_attackers_prop = '''
  selected_byzantine_nodes: {selected_byzantine_nodes}
'''

num_attackers_prop = '''
  attackers: {attackers}
'''

def do_replacement(experiment_name, i, config_dict, routing_algorithm,
                   attackers, receiver_delay_ms, concurrent_transactions_count,
                   attack_type, selected_attackers, arrival_delay_ms):
    config_formatted = config.format(
        data_set_name=config_dict['data_set_list'][i],
        epoch_length=config_dict['epoch_lengths_list'][i],
        attempts=config_dict.get('attempts',1),
        force_overwrite=config_dict.get('force_overwrite', False),
        iterations=config_dict.get('iterations', 1),
        simulation_type=config_dict.get('simulation_type', 'dynamic'),
        trees=config_dict.get('trees', 3),
        network_latency_ms=config_dict.get('network_latency_ms', 0),
        jvm_options=config_dict.get('jvm_options', '-showversion'),
        receiver_delay_variability=config_dict.get('receiver_delay_variability', 0),
        notes=config_dict.get('notes', ''),
        experiment_name=experiment_name,
        routing_algorithm=routing_algorithm,
        receiver_delay_ms=receiver_delay_ms,
        arrival_delay_ms=arrival_delay_ms,
        concurrent_transactions_count=concurrent_transactions_count,

        # if there is only one transaction at a time, transactions are not concurrent
        concurrent_transactions=(concurrent_transactions_count != 1))

    if attack_type:
        attack_config_formatted = attack_config.format(
            attack_type=attack_type,
            attacker_selection=config_dict.get('attacker_selection', None),
            receiver_delay_ms=receiver_delay_ms)
        config_formatted+=attack_config_formatted

        if isinstance(selected_attackers, pd.DataFrame):
            selected_attackers_prop_formatted = selected_attackers_prop.format(
                selected_byzantine_nodes=selected_attackers.index.values.tolist())
            config_formatted+=selected_attackers_prop_formatted
        elif selected_attackers:
            selected_attackers_prop_formatted = selected_attackers_prop.format(
                selected_byzantine_nodes=selected_attackers)
            config_formatted+=selected_attackers_prop_formatted
        else:
            num_attackers_prop_formatted = num_attackers_prop.format(
                attackers=attackers)
            config_formatted+=num_attackers_prop_formatted

    return common.parse_config(config_formatted)

# epoch_lengths_list: a list of the epoch lengths for each data set, indexes should match. See RunConfig.java to learn what the properties mean.
def generate_configs(experiment_name, config_dict):

    if 'attackers' in config_dict and 'selected_byzantine_nodes' in config_dict:
        raise Exception("Cannot provide both 'attackers' and 'selected_byzantine_nodes'")

    # If we want to select specific attackers
    if 'selected_byzantine_nodes' in config_dict:

        # the attackers are *not* a list of ints
        if not isinstance(config_dict['selected_byzantine_nodes'][0][0], int):
            byzantine_nodes = []
            # the first item is the selection criterion and the second item is
            # either the number of nodes to select, or a list of specific
            # attackers.
            for i, t  in enumerate(config_dict['selected_byzantine_nodes']):
                selection_type, num_attackers = t
                if selection_type == 'betweenness_centrality':
                    if len(config_dict['data_set_list']) > 1:
                        raise NotImplementedError("Because of some complictions with "
                                                  "indexing on the next line, we"
                                                  "cannot support multiple datasets "
                                                  "per experiment right now.")
                    n = get_nodes(f"pcn-topologies/datasets/{config_dict['data_set_list'][0]}/betweenness_centrality.txt", num_attackers)
                    byzantine_nodes.append(n)
                elif selection_type == 'none' or selection_type == 'baseline':
                    byzantine_nodes.append([])
                elif selection_type == 'by_number_of_transactions':
                    p = config_dict['exp_path']
                    n = pcn.get_top_n_nodes_by_transaction_count(num_attackers,
                                                                 exp_path=p)
                    byzantine_nodes.append(n)
                elif selection_type == 'by_tree_depth':
                    p = config_dict['exp_path']
                    n = pcn.get_top_n_nodes_by_tree_depth(num_attackers, exp_path=p)
                    byzantine_nodes.append(n)
                elif selection_type == 'selected':
                    # in this case, num attackers is actually a list of attackers
                    byzantine_nodes.append(num_attackers)

                config_dict['selected_byzantine_nodes'] = byzantine_nodes

    # TODO the attackers are a list of ints, then treat those ints as the node IDs of the attackers
    l = [do_replacement(experiment_name, i, config_dict,
                        routing_algorithm=alg,
                        attackers=att, receiver_delay_ms=rec,
                        concurrent_transactions_count=c_txs,
                        attack_type=attack_type,
                        # selected_attackers=[i for i in selected_byzantine_nodes_upto])
                        selected_attackers=selected_byzantine_nodes,
                        arrival_delay_ms=arrival_delay)

         for i in range(0, len(config_dict['data_set_list']))
         for alg in config_dict['routing_algorithms']
         for att in config_dict.get('attackers', [0])
         for rec in config_dict.get('receiver_delay_ms', [0])
         for c_txs in config_dict.get('concurrent_transactions_count', [1])
         for attack_type in config_dict.get('attack_type', [None])
         # for selected_byzantine_nodes_upto in config_dict.get('selected_byzantine_nodes_upto', [None])
         for selected_byzantine_nodes in config_dict.get('selected_byzantine_nodes', [None])
         for arrival_delay in config_dict.get('arrival_delay_ms', [0])
    ]

        
    return [l]

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
            "concurrent_transactions_count":[10],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[25],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[50],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-100-id8-mfct" : {
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[100],
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
            "concurrent_transactions_count":[100],
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[50],
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[25],
            "data_set_list":["id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-10-single-step-id8-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[10],
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
            "concurrent_transactions_count":[100],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-50-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[50],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-25-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[25],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        "dynamic-variable-conurrent-txs-10-single-step-id0-mf-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[10],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow, common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250]
        },
        ########################
        "dynamic-baseline-sequential-single-step-id0-mfct" : {
            "num_steps":1,
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-10-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[10],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-25-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[25],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-50-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[50],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-100-single-step-id0-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[100],
            "data_set_list":["id0-synthetic-nodes-100k-txs-1m-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        ########################
        "dynamic-baseline-sequential-single-step-id1-mfct" : {
            "num_steps":1,
            "data_set_list":["id1-synthetic-nodes-10k-txs-100k-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-10-single-step-id1-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[10],
            "data_set_list":["id1-synthetic-nodes-10k-txs-100k-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-25-single-step-id1-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[25],
            "data_set_list":["id1-synthetic-nodes-10k-txs-100k-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-50-single-step-id1-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[50],
            "data_set_list":["id1-synthetic-nodes-10k-txs-100k-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-100-single-step-id1-mfct" : {
            "num_steps":1,
            "concurrent_transactions_count":[100],
            "data_set_list":["id1-synthetic-nodes-10k-txs-100k-scalefree"],
            "routing_algorithms":[common.maxflow_collateralize_total],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        ########################
        "dynamic-baseline-sequential-single-step-id10-mfct-mfcs-sm" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected"],
            "routing_algorithms":[common.maxflow_collateralize_total, common.maxflow, common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-10-single-step-id10-mfct-mfcs-sm" : {
            "num_steps":1,
            "concurrent_transactions_count":[10],
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected"],
            "routing_algorithms":[common.maxflow_collateralize_total, common.maxflow, common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-25-single-step-id10-mfct-mfcs-sm" : {
            "num_steps":1,
            "concurrent_transactions_count":[25],
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected"],
            "routing_algorithms":[common.maxflow_collateralize_total, common.maxflow, common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-50-single-step-id10-mfct-mfcs-sm" : {
            "num_steps":1,
            "concurrent_transactions_count":[50],
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected"],
            "routing_algorithms":[common.maxflow_collateralize_total, common.maxflow, common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        "dynamic-variable-concurrent-txs-100-single-step-id10-mfct-mfcs-sm" : {
            "num_steps":1,
            "concurrent_transactions_count":[100],
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected"],
            "routing_algorithms":[common.maxflow_collateralize_total, common.maxflow, common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":5
        },
        ######################
        "dynamic-mfcs-griefing-attackers-0%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[100],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":0,
            "receiver_delay_ms":100
        },
        "dynamic-mfcs-griefing-attackers-5%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[100],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[500],
            "receiver_delay_ms":[100]
        },
        "dynamic-mfcs-griefing-attackers-10%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[100],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[1000],
            "receiver_delay_ms":[100]
        },
        "dynamic-mfcs-griefing-attackers-20%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[100],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[2000],
            "receiver_delay_ms":[100]
        },
        "dynamic-mfcs-griefing-attackers-30%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[100],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[3000],
            "receiver_delay_ms":[100]
        },
        ######################
        "dynamic-mfcs-griefing-attackers-0%-concurrent-txs-1000" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow,common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[0],
            "receiver_delay_ms":[1000]
        },

        "dynamic-mfcs-griefing-attackers-5%-concurrent-txs-1000" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow,common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[500],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-attackers-10%-concurrent-txs-1000" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow,common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[1000],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-attackers-20%-concurrent-txs-1000" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow,common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[2000],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-attackers-30%-concurrent-txs-1000" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow,common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[3000],
            "receiver_delay_ms":[1000]
        },
        ######################
        "dynamic-mfcs-griefing-success-attackers-0%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5","id12-synthetic-nodes-10k-txs-constant-1m-scalefree-less-connected"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-success-attackers-5%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5","id12-synthetic-nodes-10k-txs-constant-1m-scalefree-less-connected"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[500],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-success-attackers-10%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5","id12-synthetic-nodes-10k-txs-constant-1m-scalefree-less-connected"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[1000],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-success-attackers-20%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5","id12-synthetic-nodes-10k-txs-constant-1m-scalefree-less-connected"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[2000],
            "receiver_delay_ms":[1000]
        },
        "dynamic-mfcs-griefing-success-attackers-30%" : {
            "num_steps":1,
            "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected","id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5","id12-synthetic-nodes-10k-txs-constant-1m-scalefree-less-connected"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[3000],
            "receiver_delay_ms":[1000]
        },
        ########################
        "dynamic-mfcs-sm-griefing-attackers-variable": {
            "num_steps":1,
            "data_set_list":[
                "id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected",
                "id13-synthetic-nodes-10k-txs-poisson-1m-scalefree2",
                "id14-synthetic-nodes-10k-txs-exponential-1m-scalefree2",
                "id15-synthetic-nodes-10k-txs-poisson-1m-scalefree2-mult-0.5-prob-0.5"
            ],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1250,1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing"],
            "attacker_selection":"random",
            "attackers":[0, 500, 1000, 2000, 3000],
            "receiver_delay_ms":[30000]
        },
        #########################
        # "dynamic-mfcs-sm-griefing-success-attackers-variable-10000txs" : {
        #     "num_steps":1,
        #     "data_set_list":["id10-synthetic-nodes-10k-txs-1m-scalefree-less-connected",
        #                      "id8-synthetic-nodes-10k-txs-1m-scalefree-less-connected-mult-0.5",
        #                      "id15-synthetic-nodes-10k-txs-poisson-1m-scalefree2-mult-0.5-prob-0.5",
        #                      "id16-synthetic-nodes-10k-txs-normal-1m-scalefree-less-connected",
        #                      "id17-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5"],
        #     "concurrent_transactions_count":10000,
        #     "routing_algorithms":[common.maxflow],
        #     "epoch_lengths_list":[1250,1250,1250,1250,1250,1250,1250],
        #     "network_latency_ms":1,
        #     "attack_type":["griefing_success"],
        #     "attacker_selection":"random",
        #     "attackers":[0, 500, 1000, 2000, 3000],
        #     "receiver_delay_ms":[30000]
        # },
        
        ########################
        "ripple_dynamic" : {
            "data_set_list":["id3-measured-nodes-93502-txs-1m-ripple-dynamic"],
            "routing_algorithms":[common.speedymurmurs,
                                  common.silentwhispers],
            "epoch_lengths_list":[165552.45497208898],
        },
        ########################        
        "2" : {
            "notes" : "Reducing jvm stack space per thread",
            "num_steps":1,
            "data_set_list":["id17-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5",
                             "id18-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 4000],
            "receiver_delay_ms":[30000]
        },
        "3" : {
            "notes" : "Increase JVM stack a little bit and rerun",
            "num_steps":1,
            "data_set_list":["id17-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5",
                             "id18-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "4" : {
            "notes" : "Attackers will greif payment if they are *anywhere* on the path",
            "num_steps":1,
            "data_set_list":["id17-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5",
                             "id18-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "5" : {
            "notes" : "Change JVM stack size back to default",
            "num_steps":1,
            "data_set_list":["id17-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5",
                             "id18-synthetic-nodes-10k-txs-normal-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "6" : {
            "notes" : "Try attack on new data set where credit is only assigned for 80% of transactions",
            "num_steps":1,
            "data_set_list":["id19-synthetic-nodes-10k-txs-normal-1m-scalefree2-txinclusion-0.8"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "7" : {
            "notes" : "The ripple tests should be available for posterity",
            "data_set_list":["id3-measured-nodes-93502-txs-1m-ripple-dynamic"],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[165552.45497208898],
        },
        "8" : {
            "notes" : "Try three new datasets",
            "num_steps":1,
            "data_set_list":["id20-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2",
                             "id21-synthetic-poisson-nodes-10k-txs-normal-1m-scalefree2",
                             "id22-synthetic-poisson-nodes-10k-txs-constant-1m-scalefree2"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1250,1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "9" : {
            "notes" : "Attempt to get rid of spike in success ratio by increasing JVM max heap size",
            "num_steps":1,
            "data_set_list":["id20-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "jvm_options": "-Xmx64g",
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "10" : {
            "notes" : "Next attempt to get rid of spike in success ratio by increasing JVM max heap size",
            "num_steps":1,
            "data_set_list":["id20-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "jvm_options": "-XX:+AggressiveHeap",
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[5000],
            "receiver_delay_ms":[30000]
        },
        "11" : {
            "notes" : "Next attempt to get rid of spike in success ratio by increasing JVM per thread stack size",
            "num_steps":1,
            "data_set_list":["id20-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "jvm_options": "-Xss2048k",
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[30000]
        },
        "12" : {
            "notes" : "Do sensitivity on the delay, see if spike in succR is related",
            "num_steps":1,
            "data_set_list":["id20-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[5000],
            "receiver_delay_ms":[1000, 10000, 20000, 30000]
        },
        "13" : {
            "notes" : "Try new data sets with multipliers on balances",
            "num_steps":1,
            "data_set_list":["id23-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2-mult-0.5-prob-0.5",
                             "id24-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2-mult-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250,1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_ms":[10000]
        },
        "14" : {
            "notes" : "Build randomness into attack delays",
            "num_steps":1,
            "data_set_list":["id23-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 500, 5000],
            "receiver_delay_variability": 50,
            "receiver_delay_ms":[10000]
        },
        "15" : {
            "notes" : "Vary number of concurrent transactions",
            "num_steps":1,
            "data_set_list":["id23-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[2000, 4000, 8000, 10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1250],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "attacker_selection":"random",
            "attackers":[5000],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000]
        },
        "16" : {
            "notes" : "Change epoch size to 1",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[1000, 10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing", "griefing_success", "drop_all"],
            "attacker_selection":"random",
            "attackers":[0, 5000],
            "receiver_delay_variability": 50,
            "receiver_delay_ms":[10000]
        },
        "17" : {
            "notes" : "Attacker sensitivity on large network",
            "num_steps":1,
            "data_set_list":["id26-synthetic-poisson-nodes-100k-txs-pareto-1M-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing", "griefing_success"],
            "attacker_selection":"random",
            "attackers":[0, 5000, 10000, 20000, 30000],
            "receiver_delay_variability": 50,
            "receiver_delay_ms":[10000]
        },
        "18" : {
            "notes" : "Baseline for sequential speedymurmurs",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[1],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1000],
            "attack_type":["drop_all"],
            "attacker_selection":"random",
            "attackers":[0, 500, 1000, 2000, 3000]
        },
        "19" : {
            "notes" : "Check if spike is still appearing",
            "num_steps":1,
            "data_set_list":["id23-synthetic-poisson-nodes-10k-txs-pareto-1m-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["drop_all"],
            "attacker_selection":"random",
            "attackers":[0, 5000]
        },
        "20" : {
            "notes" : "Attempt to get rid of spike by JVM tuning",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[1000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"random",
            "attackers":[5000],
            "jvm_options": "-Xms64g",
            "force_overwrite": True
        },
        "21" : {
            "notes" : "Attackers chosen by number of connections",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success","griefing"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[[i for i in range(0,j)] for j in [0, 500, 1000, 2000, 3000]]
        },
        "22" : { #fig 5
            "notes" : "Get baseline for griefing random on dataset 25",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success", "griefing", "drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"random",
            "attackers":[0, 500, 1000, 2000, 3000]
        },
        "23" : { # fig 7
            "notes" : "Try selecting as few attackers as possible",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success", "griefing", "drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5/betweenness_centrality.txt", j) for j in [10, 50, 100, 300, 500]]
        },
        "24" : {
            "notes" : "Try larger hop delays",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000, 30000],
            "attacker_selection":"random",
            "attackers":[0, 500, 1000, 2000, 3000]
        },
        # "25" : {
        #     "notes" : "Random and smallworld topologies, random selection",
        #     "num_steps":1,
        #     "data_set_list":[
        #         # "id27-synthetic-poisson-nodes-10k-txs-pareto-100k-random-p0.001-mult-0.5-prob-0.5",
        #         # "id28-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5"
        #         "id29-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5"
        #     ],
        #     "concurrent_transactions_count":[10000],
        #     "routing_algorithms":[common.speedymurmurs
        #                           # ,common.maxflow
        #     ],
        #     "epoch_lengths_list":[1,1],
        #     "network_latency_ms":30,
        #     "attack_type":["griefing_success"],
        #     "receiver_delay_variability": 0,
        #     "receiver_delay_ms":[10000],
        #     "attacker_selection":"random",
        #     "attackers":[0, 500, 1000, 2000, 3000]
        # },
        "25" : {
            "notes" : "Random and smallworld topologies, random selection",
            "num_steps":1,
            "data_set_list":[
                # "id29-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                # "id31-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                "id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100"
            ],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success","drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"random",
            "attackers":[500, 1000, 2000, 3000]
        },
        "26" : {
            "notes" : "Random topology, betweenness centrality selection",
            "num_steps":1,
            "data_set_list":["id27-synthetic-poisson-nodes-10k-txs-pareto-100k-random-p0.001-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id27-synthetic-poisson-nodes-10k-txs-pareto-100k-random-p0.001-mult-0.5-prob-0.5/betweenness_centrality.txt", j) for j in [10, 50, 100, 300, 500]]
        },
        "27" : {
            "notes" : "Smallworld topology, betweenness centrality selection",
            "num_steps":1,
            "data_set_list":["id28-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id28-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5/betweenness_centrality.txt", j) for j in [10, 50, 100, 300, 500]]
        },
        # "28" : {
        #     "notes" : "large topology, betweenness centrality selection",
        #     "num_steps":1,
        #     "data_set_list":["id26-synthetic-poisson-nodes-100k-txs-pareto-1M-scalefree2-mult-0.5-prob-0.5"],
        #     "concurrent_transactions_count":[10000],
        #     "routing_algorithms":[common.speedymurmurs],
        #     "epoch_lengths_list":[1],
        #     "network_latency_ms":30,
        #     "attack_type":["griefing_success"],
        #     "receiver_delay_variability": 0,
        #     "receiver_delay_ms":[10000],
        #     "attacker_selection":"selected",
        #     "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id26-synthetic-poisson-nodes-100k-txs-pareto-1M-scalefree2-mult-0.5-prob-0.5/betweenness_centrality.txt", j) for j in [10, 50, 100, 300, 500, 1000]]
        # }
        "29" : {
            "notes" : "try new toplogies",
            "num_steps":1,
            "data_set_list":[
                # "id29-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                # "id31-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                "id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100",
                "id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100"
            ],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1,1],
            "network_latency_ms":30,
            # "attack_type":["griefing_success"],
            # "receiver_delay_variability": 0,
            # "receiver_delay_ms":[10000],
            # "attacker_selection":"random",
            # "attackers":[0]
        },
        "30" : { # fig 11
            "notes" : "Smallworld topology, betweenness centrality selection",
            "num_steps":1,
            "data_set_list":["id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success","drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100/betweenness_centrality.txt", j) for j in [10, 50, 100, 300, 500]]
        },
        "31" : {
            "notes" : "scalefree topology with minimum balances, random attackers",
            "num_steps":1,
            "data_set_list":["id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success","drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"random",
            "attackers":[500, 1000, 2000, 3000]
        },
        "32" : {
            "notes" : "scalefree topology with minimum balances, betweenness centrality attackers",
            "num_steps":1,
            "data_set_list":["id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success","drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100/betweenness_centrality.txt", j) for j in [10, 50, 100, 300, 500]]
        },
        "33" : {
            "notes" : "Betweenness centrality attackers on large network",
            "num_steps":1,
            "data_set_list":["id26-synthetic-poisson-nodes-100k-txs-pareto-1M-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow,
                                  common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success","drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id26-synthetic-poisson-nodes-100k-txs-pareto-1M-scalefree2-mult-0.5-prob-0.5/betweenness_centrality.txt", j) for j in [0, 10, 50, 100, 300, 500]],
        },
        "34" : {
            "notes" : "remove some rebalancing logic to try to get rid of spike",
            "num_steps":1,
            "data_set_list":["id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100/betweenness_centrality.txt", j) for j in [0, 10, 50, 100, 300, 500]],
            "force_overwrite": True
        },
        "35" : {
            "notes" : "Add arrival delay",
            "num_steps":1,
            "data_set_list":["id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100/betweenness_centrality.txt", j) for j in [0, 10, 50, 100, 300, 500]],
            "arrival_delay_ms": [30]
        },
        "36" : {
            "notes" : "Lightning topology",
            "num_steps":1,
            "data_set_list":["id36-measured-nodes-2337-connected-lightning-network-05282020-link-multiplier"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id36-measured-nodes-2337-connected-lightning-network-05282020-link-multiplier/betweenness_centrality.txt", j) for j in [0, 10, 50, 100, 300, 500]],
            "force_overwrite": True
        },
        "37" : {
            "notes" : "Vary arrival delay",
            "num_steps":1,
            "data_set_list":["id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100/betweenness_centrality.txt", j) for j in [3000]],
            "arrival_delay_ms": [0, 5, 10, 20, 30]
        },
        "38" : {
            "notes" : "Lightning topology with our weight assignments and less concurrent txs",
            "num_steps":1,
            "data_set_list":["id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment"],
            "concurrent_transactions_count":[2000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment/betweenness_centrality.txt", j) for j in [0, 10, 50, 100, 300, 500]],
            "force_overwrite": True
        },
        "39" : {
            "notes" : "Lightning topology with variable concurrency",
            "num_steps":1,
            "data_set_list":["id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment"],
            "concurrent_transactions_count":[2000, 5000, 7500, 10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment/betweenness_centrality.txt", j) for j in [0, 500]],
            "force_overwrite": True
        },
        "40" : {
            "notes" : "Lightning topology. fk balance assignment. 10k concurrent txs",
            "num_steps":1,
            "data_set_list":["id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment/betweenness_centrality.txt", j) for j in [0, 10, 50, 100, 300, 500]],
        },
        "41" : { # fig 9
            "notes" : "Lightning topology. fk balance assignment. 10k concurrent txs. attackers chosen by %",
            "num_steps":1,
            "data_set_list":["id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[get_nodes("pcn-topologies/datasets/id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment/betweenness_centrality.txt", j) for j in [0, 3, 7, 12, 24, 71, 117]],
        },
        "42" : { # corresponds to 22 and 41
            "notes" : "Get baseline for griefing random on dataset 25",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5",
                             "id37-measured-nodes-2337-connected-lightning-network-05282020-fk-link-assignment",
                             "id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100"],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1, 1, 1]
        },
        "43" : {
            "notes" : "Get more datapoints for GRS/EG correlation, sequential runs",
            "num_steps":1,
            "concurrent_transactions_count":[1],
            "data_set_list":["id27-synthetic-poisson-nodes-10k-txs-pareto-100k-random-p0.001-mult-0.5-prob-0.5",
                             "id28-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id29-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id30-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id31-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100",
                             "id39-synthetic-powerlaw-nodes-10k-txs-pareto-100k-scalefree5-mult-0.5-prob-0.5",
                             "id40-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-1.0-prob-1.0",
                             "id41-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-1.0-prob-1.0-min50",
                             "id42-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-1.0-prob-1.0-min100",
                             "id43-synthetic-poisson-nodes-10000-txs-pareto-100000-smallworldk20-p0.01-mult-1.0-prob-1.0",
                             "id44-synthetic-poisson-nodes-10000-txs-pareto-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min50",
                             "id45-synthetic-poisson-nodes-10000-txs-pareto-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min100",
                             #"id46-synthetic-poisson-nodes-10000-txs-pareto-100000-random0.001-mult-1.0-prob-1.0",
                             #"id47-synthetic-poisson-nodes-10000-txs-pareto-100000-random0.001-mult-1.0-prob-1.0-min50",
                             #"id48-synthetic-poisson-nodes-10000-txs-pareto-100000-random0.001-mult-1.0-prob-1.0-min100",
                             "id49-synthetic-poisson-nodes-10000-txs-poisson-100000-scalefree2-mult-1.0-prob-1.0",
                             "id50-synthetic-poisson-nodes-10000-txs-poisson-100000-scalefree2-mult-1.0-prob-1.0-min50",
                             "id51-synthetic-poisson-nodes-10000-txs-poisson-100000-scalefree2-mult-1.0-prob-1.0-min100",
                             "id52-synthetic-poisson-nodes-10000-txs-poisson-100000-smallworldk20-p0.01-mult-1.0-prob-1.0",
                             "id53-synthetic-poisson-nodes-10000-txs-poisson-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min50",
                             "id54-synthetic-poisson-nodes-10000-txs-poisson-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min100",
                             #"id55-synthetic-poisson-nodes-10000-txs-poisson-100000-random0.001-mult-1.0-prob-1.0",
                             #"id56-synthetic-poisson-nodes-10000-txs-poisson-100000-random0.001-mult-1.0-prob-1.0-min50",
                             #"id57-synthetic-poisson-nodes-10000-txs-poisson-100000-random0.001-mult-1.0-prob-1.0-min100",
                             "id58-synthetic-poisson-nodes-10000-txs-exponential-100000-scalefree2-mult-1.0-prob-1.0",
                             "id59-synthetic-poisson-nodes-10000-txs-exponential-100000-scalefree2-mult-1.0-prob-1.0-min50",
                             "id60-synthetic-poisson-nodes-10000-txs-exponential-100000-scalefree2-mult-1.0-prob-1.0-min100",
                             "id61-synthetic-poisson-nodes-10000-txs-exponential-100000-smallworldk20-p0.01-mult-1.0-prob-1.0",
                             "id62-synthetic-poisson-nodes-10000-txs-exponential-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min50",
                             "id63-synthetic-poisson-nodes-10000-txs-exponential-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min100",
                             #"id64-synthetic-poisson-nodes-10000-txs-exponential-100000-random0.001-mult-1.0-prob-1.0",
                             #"id65-synthetic-poisson-nodes-10000-txs-exponential-100000-random0.001-mult-1.0-prob-1.0-min50",
                             #"id66-synthetic-poisson-nodes-10000-txs-exponential-100000-random0.001-mult-1.0-prob-1.0-min100"
                             ],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]
        },
        "44" : {
            "notes" : "Get more datapoints for GRS/EG correlation, concurrent/attacker runs",
            "num_steps":1,
            "concurrent_transactions_count":[10000],
            "data_set_list":["id27-synthetic-poisson-nodes-10k-txs-pareto-100k-random-p0.001-mult-0.5-prob-0.5",
                             "id28-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id29-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id30-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id31-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5",
                             "id34-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree-mult-0.5-prob-0.5-min-100",
                             "id39-synthetic-powerlaw-nodes-10k-txs-pareto-100k-scalefree5-mult-0.5-prob-0.5",
                             "id40-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-1.0-prob-1.0",
                             "id41-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-1.0-prob-1.0-min50",
                             "id42-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-1.0-prob-1.0-min100",
                             "id43-synthetic-poisson-nodes-10000-txs-pareto-100000-smallworldk20-p0.01-mult-1.0-prob-1.0",
                             "id44-synthetic-poisson-nodes-10000-txs-pareto-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min50",
                             "id45-synthetic-poisson-nodes-10000-txs-pareto-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min100",
                             #"id46-synthetic-poisson-nodes-10000-txs-pareto-100000-random0.001-mult-1.0-prob-1.0",
                             #"id47-synthetic-poisson-nodes-10000-txs-pareto-100000-random0.001-mult-1.0-prob-1.0-min50",
                             #"id48-synthetic-poisson-nodes-10000-txs-pareto-100000-random0.001-mult-1.0-prob-1.0-min100",
                             "id49-synthetic-poisson-nodes-10000-txs-poisson-100000-scalefree2-mult-1.0-prob-1.0",
                             "id50-synthetic-poisson-nodes-10000-txs-poisson-100000-scalefree2-mult-1.0-prob-1.0-min50",
                             "id51-synthetic-poisson-nodes-10000-txs-poisson-100000-scalefree2-mult-1.0-prob-1.0-min100",
                             "id52-synthetic-poisson-nodes-10000-txs-poisson-100000-smallworldk20-p0.01-mult-1.0-prob-1.0",
                             "id53-synthetic-poisson-nodes-10000-txs-poisson-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min50",
                             "id54-synthetic-poisson-nodes-10000-txs-poisson-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min100",
                             #"id55-synthetic-poisson-nodes-10000-txs-poisson-100000-random0.001-mult-1.0-prob-1.0",
                             #"id56-synthetic-poisson-nodes-10000-txs-poisson-100000-random0.001-mult-1.0-prob-1.0-min50",
                             #"id57-synthetic-poisson-nodes-10000-txs-poisson-100000-random0.001-mult-1.0-prob-1.0-min100",
                             "id58-synthetic-poisson-nodes-10000-txs-exponential-100000-scalefree2-mult-1.0-prob-1.0",
                             "id59-synthetic-poisson-nodes-10000-txs-exponential-100000-scalefree2-mult-1.0-prob-1.0-min50",
                             "id60-synthetic-poisson-nodes-10000-txs-exponential-100000-scalefree2-mult-1.0-prob-1.0-min100",
                             "id61-synthetic-poisson-nodes-10000-txs-exponential-100000-smallworldk20-p0.01-mult-1.0-prob-1.0",
                             "id62-synthetic-poisson-nodes-10000-txs-exponential-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min50",
                             "id63-synthetic-poisson-nodes-10000-txs-exponential-100000-smallworldk20-p0.01-mult-1.0-prob-1.0-min100",
                             #"id64-synthetic-poisson-nodes-10000-txs-exponential-100000-random0.001-mult-1.0-prob-1.0",
                             #"id65-synthetic-poisson-nodes-10000-txs-exponential-100000-random0.001-mult-1.0-prob-1.0-min50",
                             #"id66-synthetic-poisson-nodes-10000-txs-exponential-100000-random0.001-mult-1.0-prob-1.0-min100"
                             ],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
            "network_latency_ms":30,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("betweenness_centrality", 500)],
        },
        "45" : { #fig 5
            "notes" : "Get baseline for griefing random on dataset 25, w/ tree stats",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success", "griefing", "drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"random",
            "attackers":[0, 500, 1000, 2000, 3000],
            "force_overwrite": True
        },
        "46" : {
            "notes" : "Select tree roots as attackers",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success", "griefing", "drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("selected", [64, 36, 43]), ("baseline", 0)],
        },
        "47" : {
            "notes" : "Try out new topo that has random participant distro",
            "num_steps":1,
            "data_set_list":["id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs, common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success", "griefing", "drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"random",
            "attackers":[0, 500, 1000, 2000, 3000],
            "force_overwrite": True
        },
        "48-prep" : {
            "notes" : "Try out new topo that has random participant distro",
            "num_steps":1,
            "data_set_list":["id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True
        },
        "48" : {
            "notes" : "Try out new topo that has random participant distro",
            "num_steps":1,
            "data_set_list":["id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0), ("by_number_of_transactions", 1), ("by_number_of_transactions", 10), ("by_number_of_transactions", 100), ("by_number_of_transactions", 1000), ("by_number_of_transactions", 2000), ("by_number_of_transactions", 3000)],
            "exp_path":"data/dynamic-id68-48-prep/dynamic-id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/0/CREDIT_NETWORK-SM-P0-1.0-TREE_ROUTE_TDRAP-true-false-3-0.002-RANDOM_PARTITIONER-1/",
            "force_overwrite": True
        },
        "49-prep" : {
            "notes" : "Select attackers by # of txs on a trusted dataset",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": False
        },        
        "49" : {
            "notes" : "Select attackers by # of txs on a trusted dataset",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_number_of_transactions", 1),
                                        ("by_number_of_transactions", 10),
                                        ("by_number_of_transactions", 100),
                                        ("by_number_of_transactions", 1000),
                                        ("by_number_of_transactions", 2000),
                                        ("by_number_of_transactions", 3000)],
            "exp_path":"data/dynamic-id25-49-prep/dynamic-id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/0/CREDIT_NETWORK-SM-P0-1.0-TREE_ROUTE_TDRAP-true-false-3-0.002-RANDOM_PARTITIONER-1/",
            "force_overwrite": False
        },
        "50" : { # fig 7, sm only
            "notes" : "Try selecting as few attackers as possible",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("betweenness_centrality", 10),
                                        ("betweenness_centrality", 50),
                                        ("betweenness_centrality", 100),
                                        ("betweenness_centrality", 300),
                                        ("betweenness_centrality", 500)],
            "force_overwrite": True
        },
        "51-prep" : {
            "notes" : "Select attackers by tree depth",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True            
        },
        "51" : {
            "notes" : "Select attackers by tree depth",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_tree_depth", 10),
                                        ("by_tree_depth", 50),
                                        ("by_tree_depth", 100),
                                        ("by_tree_depth", 300),
                                        ("by_tree_depth", 500)],
            "exp_path":"data/dynamic-id25-51-prep/dynamic-id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/",
            "force_overwrite": True
        },
        "52-prep" : {
            "notes" : "Select attackers by tree depth, random sender/reciever distro",
            "num_steps":1,
            "data_set_list":["id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True            
        },
        "52" : {
            "notes" : "Select attackers by tree depth",
            "num_steps":1,
            "data_set_list":["id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_tree_depth", 10),
                                        ("by_tree_depth", 50),
                                        ("by_tree_depth", 100),
                                        ("by_tree_depth", 300),
                                        ("by_tree_depth", 500)],
            "exp_path":"data/dynamic-id68-52-prep/dynamic-id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/",
            "force_overwrite": True
        },
        "53-prep" : {
            "notes" : "Try a topo with a higher success ratio",
            "num_steps":1,
            "data_set_list":["id69-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.75-prob-0.25"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True            
        },
        "53" : {
            "notes" : "Select attackers by tree depth",
            "num_steps":1,
            "data_set_list":["id69-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.75-prob-0.25"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_tree_depth", 10),
                                        ("by_tree_depth", 50),
                                        ("by_tree_depth", 100),
                                        ("by_tree_depth", 300),
                                        ("by_tree_depth", 500)],
            "exp_path":"data/dynamic-id69-53-prep/dynamic-id69-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.75-prob-0.25-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/",
            "force_overwrite": True
        },
        "54-prep" : {
            "notes" : "Try a topo with a higher success ratio",
            "num_steps":1,
            "data_set_list":["id70-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.9-prob-0.1"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True            
        },
        "54" : {
            "notes" : "Select attackers by tree depth",
            "num_steps":1,
            "data_set_list":["id70-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.9-prob-0.1"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_tree_depth", 10),
                                        ("by_tree_depth", 50),
                                        ("by_tree_depth", 100),
                                        ("by_tree_depth", 300),
                                        ("by_tree_depth", 500)],
            "exp_path":"data/dynamic-id70-54-prep/dynamic-id70-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.9-prob-0.1-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/",
            "force_overwrite": True
        },
        "55" : {
            "notes" : "Select attackers by centrality",
            "num_steps":1,
            "data_set_list":["id70-synthetic-poisson-nodes-10000-txs-pareto-100000-scalefree2-mult-0.9-prob-0.1"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("betweenness_centrality", 10),
                                        ("betweenness_centrality", 50),
                                        ("betweenness_centrality", 100),
                                        ("betweenness_centrality", 300),
                                        ("betweenness_centrality", 500)],
            "force_overwrite": True
        },
        "56-prep" : {
            "notes" : "Select attackers by # of txs on a trusted dataset for maxflow",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True
        },        
        "56" : {
            "notes" : "Select attackers by # of txs on a trusted dataset for maxflow",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_number_of_transactions", 1),
                                        ("by_number_of_transactions", 10),
                                        ("by_number_of_transactions", 100),
                                        ("by_number_of_transactions", 1000),
                                        ("by_number_of_transactions", 2000),
                                        ("by_number_of_transactions", 3000)],
            "exp_path":"data/dynamic-id25-56-prep/dynamic-id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5-maxflow_collateralize-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_M-P0-10000/0/CREDIT_MAX_FLOW-0.0-0/",
            "force_overwrite": True
        },
        "57" : {
            "notes" : "Select attackers by tree depth for dropping attack",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_tree_depth", 1),
                                        ("by_tree_depth", 10),
                                        ("by_tree_depth", 50),
                                        ("by_tree_depth", 100),
                                        ("by_tree_depth", 300),
                                        ("by_tree_depth", 500)],
            "exp_path":"data/dynamic-id25-51-prep/dynamic-id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/",
            "force_overwrite": True
        },
        "58" : {
            "notes" : "Select attackers by # of txs on a trusted dataset for maxflow",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.maxflow],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_number_of_transactions", 1),
                                        ("by_number_of_transactions", 10),
                                        ("by_number_of_transactions", 100),
                                        ("by_number_of_transactions", 1000),
                                        ("by_number_of_transactions", 2000),
                                        ("by_number_of_transactions", 3000)],
            "exp_path":"data/dynamic-id25-56-prep/dynamic-id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5-maxflow_collateralize-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_M-P0-10000/0/CREDIT_MAX_FLOW-0.0-0/",
            "force_overwrite": True
        },
        "59" : {
            "notes" : "Select attackers by # of txs on a trusted dataset",
            "num_steps":1,
            "data_set_list":["id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["drop_all"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_number_of_transactions", 1),
                                        ("by_number_of_transactions", 10),
                                        ("by_number_of_transactions", 100),
                                        ("by_number_of_transactions", 1000),
                                        ("by_number_of_transactions", 2000),
                                        ("by_number_of_transactions", 3000)],
            "exp_path":"data/dynamic-id25-49-prep/dynamic-id25-synthetic-poisson-nodes-10k-txs-pareto-100k-scalefree2-mult-0.5-prob-0.5-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/0/CREDIT_NETWORK-SM-P0-1.0-TREE_ROUTE_TDRAP-true-false-3-0.002-RANDOM_PARTITIONER-1/",
            "force_overwrite": False
        },
        "60-prep" : {
            "notes" : "Oracle attacker prep, griefing, speedymurmurs, smallworld",
            "num_steps":1,
            "data_set_list":["id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "force_overwrite": True
        },        
        "60" : {
            "notes" : "Select attackers by # of txs on a trusted dataset for maxflow",
            "num_steps":1,
            "data_set_list":["id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("baseline", 0),
                                        ("by_number_of_transactions", 1),
                                        ("by_number_of_transactions", 10),
                                        ("by_number_of_transactions", 100),
                                        ("by_number_of_transactions", 1000),
                                        ("by_number_of_transactions", 2000),
                                        ("by_number_of_transactions", 3000)],
            "exp_path":"data/dynamic-id32-60-prep/dynamic-id32-synthetic-poisson-nodes-10k-txs-pareto-100k-smallworld-mult-0.5-prob-0.5-min-100-speedymurmurs-3-1-1-lat1ms-concurrent-10000-arrivalDelay0ms/READABLE_FILE_SM-P0-10000/0/CREDIT_NETWORK-SM-P0-1.0-TREE_ROUTE_TDRAP-true-false-3-0.002-RANDOM_PARTITIONER-1/",
            "force_overwrite": True
        },
        "test" : {
            "notes" : "Try out new topo that has random participant distro",
            "num_steps":1,
            "data_set_list":["id68-synthetic-random-nodes-10000-txs-pareto-100000-scalefree2-mult-0.5-prob-0.5"],
            "concurrent_transactions_count":[10000],
            "routing_algorithms":[common.speedymurmurs],
            "epoch_lengths_list":[1],
            "network_latency_ms":1,
            "attack_type":["griefing_success"],
            "receiver_delay_variability": 0,
            "receiver_delay_ms":[10000],
            "attacker_selection":"selected",
            "selected_byzantine_nodes":[("by_tree_depth", 10)],
            "exp_path":"data/test-output/READABLE_FILE_SM-P0-100/",
            "force_overwrite": True
        },
    }

    return experiments

def get_nodes(filename, n=-1):
    with open(filename, 'r') as f:
        nodes = json.loads(f.read())
    if n == -1:
        return nodes
    else:
        return nodes[0:n]

def get_experiment_config(experiment_name):
    return generate_configs(experiment_name, get_experiments().get(experiment_name))

def get_dataset_id(experiment_name):
    dataset_name = get_experiment_config(experiment_name)[0][0]['data_set_name']
    p = re.compile('id([0-9]+)-.*')
    m = p.match(dataset_name)
    if m:
        return int(m.groups()[0])
    else:
        raise Error("Dataset name does not conform to standard.")


if __name__ == "__main__":
    exp_name = sys.argv[1]

    if exp_name == "list":
        print("+=======================+")
        print("| Available Experiments |")
        print("+=======================+")
        for k in get_experiments():
            print(k)
    elif exp_name == "print":
        exps = get_experiment_config(sys.argv[2])
        for d in exps:
            print(json.dumps(d, indent=4))
    else:
        configs = get_experiment_config(exp_name)
        attack_common.start(configs)
