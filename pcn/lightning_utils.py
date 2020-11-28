#!/usr/bin/env python3

import networkx as nx
from networkx.readwrite import json_graph
import json
import sys
import logging
import math


def __load_json(file_path):
    try:
        f = open(file_path, 'r', encoding="utf8")
    except:
        print("Could not open file: " + file_path)
        sys.exit(-1)

    try:
        json_data =json.load(f)
    except:
        logging.error("Could not parse JSON!")
        sys.exit(-1)

    for item in json_data['edges']:
        if item['capacity']:
            item['capacity'] = int(item['capacity'])

    return json_data

def __load_graph(json_data):
    G = json_graph.node_link_graph(json_data, False, False, {'name':'pub_key', 'source':'node1_pub', 'target':'node2_pub', 'key':'channel_id', 'link':'edges'})
    return G

def convert_networkx_topo_to_gtna(G, name="topology"):
    edge_map = {}
    num_nodes = len(G)
    e = nx.edges(G)

    node_ids = {}
    i = 0
    for s, d in e:
        # convert node hashes to monotonically increasing id numbers
        if s not in node_ids:
            node_ids[s] = i
            i += 1
        if d not in node_ids:
            node_ids[d] = i
            i += 1

        # print(f"s: {s}; d: {d}")
        s_str = str(node_ids[s])
        d_str = str(node_ids[d])
        if s_str in edge_map:
            edge_map[s_str].append(d_str)
        else:
            edge_map[s_str] = [d_str]

    with open(f"{name}.graph", 'w') as f:
        f.write("# Name of the Graph:\n")
        f.write(f"G (Nodes = {num_nodes})\n")
        f.write("# Number of Nodes:\n")
        f.write(f"{num_nodes}\n")
        f.write("# Number of Edges:\n")
        f.write(f"{len(e)}\n\n")

        for source, dest_list in edge_map.items():
            dest_str = ";".join(dest_list)
            f.write(f"{source}:{dest_str}\n")

    return node_ids

def convert_lightning_links_to_gtna(G, nodes_ids, name="topology"):
    with open(f"{name}.graph_CREDIT_LINKS", 'w') as f:
        f.write("# Graph Property Class\n")
        f.write("treeembedding.credit.CreditLinks\n")
        f.write("# Key\n")
        f.write("CREDIT_LINKS\n")

        # print(topo.link_credit)
        edges = {}
        for s, d in G.edges:
            if s < d:
                cur = roundup(get_edge_capacity(G, s, d))
                maxc = roundup(cur + get_edge_capacity(G, d, s))
                line = f"{node_ids[s]} {node_ids[d]} 0.0 {cur} {maxc}\n"
                f.write(line)

def get_edge_capacity(G, u, v):
    cap = 0.0
    edge_data = G.get_edge_data(u,v)
    if edge_data:
        cap = edge_data.get('capacity')
    else: print("Zero cap. Should not happen..")
    return cap

def roundup(x):
    return math.ceil(x * 10) / 10.0

def load_lightning_topo(ln_snapshot_file):
    json_data = __load_json(ln_snapshot_file)
    return __load_graph(json_data)

if __name__ == '__main__':
    ln_snapshot_file = sys.argv[1]
    G = load_lightning_topo(ln_snapshot_file)

    node_ids = convert_networkx_topo_to_gtna(G)
    convert_lightning_links_to_gtna(G, node_ids)
