#!/usr/bin/env python3

import matplotlib.pyplot as plt
import networkx as nx
from typing import Tuple

first_data_line_in_topo = 7
betweenness_centrality = 'betweenness_centrality'

selection_methods = {'betweenness_centrality': nx.betweenness_centrality}

def __parse_gtna_topology_line(line: str) -> Tuple:
    node, rest = line.split(":")
    connections = rest.strip().split(";")
    return (node, connections)

def show(G):
    nx.draw(G, pos=nx.spring_layout(G))
    plt.show()

def parse_topology(filename):
    with open(filename, 'r') as f:
        i = 0
        graph_dict = {}
        for l in f.readlines():
            # skip header
            if i < first_data_line_in_topo:
                i+=1
                continue
            node, connections = __parse_gtna_topology_line(l)
            if node not in graph_dict:
                graph_dict[node] = {}

            for con in connections:
                graph_dict[node][con] = {"weight": 1}

        return nx.Graph(graph_dict)

def __sort_dict_by_keys(d):
    return [(k, v) for k, v in sorted(d.items(), key=lambda item: item[1], reverse=True)]

def select_n_by_method(basepath, method='betweenness_centrality', top_n=1):
    filename = "/topology.graph"
    G = parse_topology(basepath + filename)
    top_n_entries = __sort_dict_by_keys(selection_methods[method](G))[0:top_n]
    return ([int(k) for k, _ in top_n_entries], [v for _, v in top_n_entries])

def calculate_betweenness_centrality_raw(path="./", topo=None, store=False):
    if topo:
        G = topo
    else:
        G = parse_topology(path + "/topology.graph")

    bc = nx.betweenness_centrality(G)
    with open(path + "/betweenness_centrality_raw.txt", "w") as f:
        f.write('\n'.join(str(bc) for node, bc in bc.items()))
