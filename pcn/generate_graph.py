#!/usr/bin/env python3

import collections
import json
import logging
import math
import matplotlib.pyplot as plt
import networkx as nx
import numpy as np
import plot_data_sets as dsplot
import random
import statistics as stat

def convert_topo_to_gtna(topo, name="topology"):
    edge_map = {}
    num_edges = len(nx.edges(topo.graph))
    for s, d in topo.edges:
        # if not topo.in_largest_connected_component(s) and topo.in_largest_connected_component(d):
        #     continue
        # num_edges += 1
        s_str = str(s)
        d_str = str(d)
        if s_str in edge_map:
            edge_map[s_str].append(d_str)
        else:
            edge_map[s_str] = [d_str]

    num_nodes = len(topo.graph)
    with open(f"{name}.graph", 'w') as f:
        f.write("# Name of the Graph:\n")
        f.write(f"G (Nodes = {num_nodes})\n")
        f.write("# Number of Nodes:\n")
        f.write(f"{num_nodes}\n")
        f.write("# Number of Edges:\n")
        f.write(f"{num_edges}\n\n")

        for source, dest_list in edge_map.items():

            dest_str = ";".join(dest_list)
            f.write(f"{source}:{dest_str}\n")

def convert_credit_links_to_gtna(topo, name="topology"):
    with open(f"{name}.graph_CREDIT_LINKS", 'w') as f:
        f.write("# Graph Property Class\n")
        f.write("treeembedding.credit.CreditLinks\n")
        f.write("# Key\n")
        f.write("CREDIT_LINKS\n")

        # print(topo.link_credit)
        edges = {}
        for s, d in topo.graph.edges:
            # if not topo.in_largest_connected_component(s) and topo.in_largest_connected_component(d):
                # continue

            if s < d:
                cur = roundup(topo.link_credit[(s,d)])
                max = roundup(cur + topo.link_credit[(d, s)])
                line = f"{s} {d} 0.0 {cur} {max}\n"
                f.write(line)


def convert_txs_to_gtna(transactions, name="transactions"):
    with open(f"{name}.txt", 'w') as f:
        counter = 0
        for source, dest, value in transactions:
            f.write(f"{counter} {rounddown(value)} {source} {dest}\n")
            counter += 1


def roundup(x):
    return math.ceil(x * 10) / 10.0

def rounddown(x):
    return math.floor(x * 10) / 10.0

def calculate_stats(topo, txs):
    stats_out = {}
    # calculate avg and median channel weights
    link_balance_list = list(topo.link_credit.values())
    stats_out['link_balance_sum'] = sum(link_balance_list)
    stats_out['link_balance_mean'] = stat.mean(link_balance_list)
    stats_out['link_balance_median'] = stat.median(link_balance_list)

    tx_vals = [value for _, _, value in txs]
    stats_out['tx_value_mean'] = stat.mean(tx_vals)
    stats_out['tx_value_median'] = stat.median(tx_vals)
    stats_out['tx_value_sum'] = sum(tx_vals)
    return stats_out

def write_stats(s, f="stats.txt"):
    with open(f, "w") as f:
        for k, v in s.items():
            f.write(f"{k}: {v}\n")

def create_newlinks_files():
    for i in range(1, 11):
        new_file = f"{new_dataset_path}/newlinks-{i}.txt"
        open(new_file, 'a').close()

    # create empty file
    open(f"{new_dataset_path}/newlinks.txt", 'a').close()


def name_dataset(configs):
    def stringify_connection_param(params):
        if isinstance(params, collections.Mapping):
            return f"k{params['k']}-p{params['p']}"
        else:
            return params

    ret = f"id{configs['id']}-synthetic-{configs['tx_participant_distribution']}-nodes-{configs['node_count']}-txs-{configs['tx_value_distribution']}-{configs['tx_count']}-{configs['base_topology']}{stringify_connection_param(configs['connection_parameter'])}-mult-{configs['value_multiplier']}-prob-{configs['multiplier_probability']}"

    if not configs['tx_inclusion_probability'] == 1.0:
        ret += f"-tx_inc{configs['tx_inclusion_probability']}"
        
    if not configs['min_channel_balance'] == 0:
        ret += f"-min{configs['min_channel_balance']}"

    return ret

def connection_parameter_type(string):
    try:
        value = int(string)
    except:
        try:
            value = float(string)
        except:            
            value = json.loads(string)

    return value

